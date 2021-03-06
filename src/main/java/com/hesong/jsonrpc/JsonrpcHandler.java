package com.hesong.jsonrpc;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;

import org.apache.log4j.Logger;

import com.hesong.weChatAdapter.runner.JsonrpcHandlerRunner;

public class JsonrpcHandler {
    private static Logger log = Logger.getLogger(JsonrpcHandler.class);

    private Object handler;

    public Object getHandler() {
        return handler;
    }

    public void setHandler(Object handler) {
        this.handler = handler;
    }

    public JsonrpcHandler(Object handler) {
        super();
        this.handler = handler;
    }

    public String handle(String jsonrpc) {
        JSONObject node = null;
        String id = null;
        String jsonrpcVesion = null;
        String methodName = null;
        JSONArray params = null;

        try {
            node = (JSONObject) JSONSerializer.toJSON(jsonrpc);//JSONObject.fromObject(jsonrpc);
            id = node.getString("id");
            // parse request
            jsonrpcVesion = node.getString("jsonrpc");

        } catch (Exception e) {
            e.printStackTrace();
            return createErrorResponse("2.0", id, 7001,
                    "Check your json content, exception: "+e.getStackTrace(), null);
        }

        // return
        if (node.has("result") || node.has("error")) {
            // Login ret
            if (JsonrpcHandlerRunner.loginAckRetQueue.containsKey(id)) {
                String ret = node.has("result")?"OK":"Failed";
                try {
                    JsonrpcHandlerRunner.loginAckRetQueue.get(id).put(ret);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                    log.error("Put result to loginAckRetQueue error: "+e.toString());
                }
            }
            
            // GetAgentRooms ret
            if (JsonrpcHandlerRunner.getRoomsRetQueue.containsKey(id)) {
                Object ret = node.has("result")?node.get("result"):null;
                if (ret != null) {
                    try {
                        JsonrpcHandlerRunner.getRoomsRetQueue.get(id).put(ret);
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                        log.error("Put result to getRoomsRetQueue error: "
                                + e.toString());
                    }
                }
            }
            return null;
        }
        
        // Invalide JSONRPC 
        if (!node.has("jsonrpc") || !node.has("method")) {
            log.error("Invalide JSONRPC: "+node.toString());
            return createErrorResponse("2.0", id, 7001, "Check your json content.", null);
        }
        methodName = node.getString("method");
        params = node.getJSONArray("params");

//        log.info("Json: " + jsonrpcVesion + " " + methodName + " " + id + " "
//                + params);

        int paramCount = (params != null) ? params.size() : 0;

        // find methods
        Set<Method> methods = findMethods(methodName);

        // method not found
        if (methods.isEmpty()) {
            return createErrorResponse(jsonrpcVesion, id, 32601,
                    "Method not found:" + methodName, null);
        }

        Iterator<Method> itr = methods.iterator();
        while (itr.hasNext()) {
            Method method = itr.next();
            if (method.getParameterTypes().length != paramCount) {
                itr.remove();
            }
        }

        if (methods.size() == 0) {
            return createErrorResponse(jsonrpcVesion, id, 32602,
                    "Invalid method parameters.", null);
        }

        // choose a method
        Method method = null;
        List<Object> paramNodes = new ArrayList<Object>();

        // handle param arrays, no params, and single methods

        if (paramCount == 0 || params.isArray() || (methods.size() == 1)) {
            method = methods.iterator().next();
            for (int i = 0; i < paramCount; i++) {
                paramNodes.add(params.get(i));
            }
            // handle named params
        } else {
            return createErrorResponse(jsonrpcVesion, id, 32602,
                    "Invalid method parameters, check your json request.", null);
        }

        // invalid parameters
        if (method == null) {
            return createErrorResponse(jsonrpcVesion, id, 32602,
                    "Invalid method parameters, check your json request.", null);
        }

        // invoke the method
        JSONObject result = null;
        JSONObject error = null;
        try {
            result = invoke(method, paramNodes);
        } catch (Exception e) {
            e.printStackTrace();
            error = new JSONObject();
            error.put("code", 9999);
            error.put("message", e.toString());
        }

        // error.put("data", mapper.valueToTree(e));

        // bail if notification request
        if (id == null) {
            return createErrorResponse(jsonrpcVesion, id, 32603, "Id is null.",
                    null);
        }

        // create response
        JSONObject response = new JSONObject();
        response.put("jsonrpc", jsonrpcVesion);
        response.put("id", id);

        if (error != null) {
            response.put("error", error);
            return response.toString();
        }
        if (result != null) {
            if (!result.has("errcode")) {
                if (result.has("FollowersInfo")) {
                    // reutrn clients info
                    response.put("result", result.get("FollowersInfo"));
                    return response.toString();
                }
                if (result.has("FollowersCount")) {
                    // reutrn clients count
                    response.put("result", result.get("FollowersCount"));
                    return response.toString();
                }
                response.put("result", result);
                return response.toString();
            }
            int errcode = result.getInt("errcode");
            if (errcode == 0) {
                response.put("result", 0);
            } else if (errcode != 0) {
                JSONObject jo = new JSONObject();
                jo.put("code", errcode);
                jo.put("message", result.getString("errmsg"));
                response.put("error", jo);
            }
        } else {
            return createErrorResponse(jsonrpcVesion, id, -32602,
                    "Invalid method parameters, check your json request.", null);
        }

        return response.toString();
    }

    /**
     * Finds methods with the given name on the {@code handler}.
     * 
     * @param name
     *            the name
     * @return the methods
     */
    private Set<Method> findMethods(String name) {
        Set<Method> ret = new HashSet<Method>();
        for (Method method : getHandler().getClass().getMethods()) {
            if (method.getName().equals(name)) {
                ret.add(method);
            }
        }
        return ret;
    }

    /**
     * Invokes the given method on the {@code handler} passing the given params
     * (after converting them to beans\objects) to it.
     * 
     * @param m
     *            the method to invoke
     * @param params
     *            the params to pass to the method
     * @return the return value (or null if no return)
     * @throws JsonParseException
     * @throws JsonMappingException
     * @throws IOException
     * @throws IllegalArgumentException
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     */
    private JSONObject invoke(Method m, List<Object> params)
            throws IllegalAccessException, IllegalArgumentException,
            InvocationTargetException {
        // convert the parameters
        Object[] convertedParams = new Object[params.size()];
        Type[] parameterTypes = m.getGenericParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            if (params.get(i).toString() == "null") {
                convertedParams[i] = null;
                continue;
            }
            convertedParams[i] = params.get(i).toString();
            // convertedParams[i] = mapper.treeToValue(params.get(i),
            // TypeFactory
            // .type(parameterTypes[i]).getRawClass());
        }

        // invoke the method
        Object result;
        result = m.invoke(handler, convertedParams);
        return (m.getGenericReturnType() != null) ? (JSONObject) result : null;

    }

    /**
     * Convenience method for creating an error response
     * 
     * @param jsonRpc
     *            the jsonrpc string
     * @param id
     *            the id
     * @param code
     *            the error code
     * @param message
     *            the error message
     * @param data
     *            the error data (if any)
     * @return the error response
     */
    private String createErrorResponse(String jsonRpc, String id, int code,
            String message, Object data) {
        JSONObject response = new JSONObject();
        JSONObject error = new JSONObject();
        error.put("code", code);
        error.put("message", message);
        if (data != null) {
            error.put("data", data.toString());
        }
        response.put("jsonrpc", jsonRpc);
        response.put("id", id);
        response.put("error", error);
        return response.toString();
    }
}
