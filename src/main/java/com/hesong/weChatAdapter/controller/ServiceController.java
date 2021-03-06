package com.hesong.weChatAdapter.controller;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;

import org.apache.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import com.hesong.jsonrpc.WeChatMethodSet;
import com.hesong.weChatAdapter.manager.MessageManager;

@Controller
@RequestMapping("/service")
public class ServiceController {


    private static Logger log = Logger.getLogger(ServiceController.class);
    
    @RequestMapping(value = "/repair", method = RequestMethod.GET)
    public String repair(){
        return "repair";
    }
    
    @SuppressWarnings("unchecked")
    @RequestMapping(value = "/editMenu", method = RequestMethod.POST, produces = "application/json; charset=utf-8")
//    public String editMenu(@RequestParam("editMenu") String editMenu){
    public @ResponseBody JSONObject editMenu(HttpServletRequest request){
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> menuProps;
        try {
            menuProps = mapper.readValue(request.getInputStream(), Map.class);
            String account = (String)menuProps.get("account");
            String action = (String)menuProps.get("action");
            String menuContent = null;
            if (action.equals("create")) {
                menuContent = ((JSONObject) JSONSerializer.toJSON(menuProps.get("menuContent"))).toString();
            }
            String accessToken = WeChatMethodSet.getAccessToken(account);
            JSONObject jo = MessageManager.manageMenu(accessToken, action, menuContent);
            return jo;

        } catch (Exception e) {
            log.error("Json mapper exception: " + e.toString());
            e.printStackTrace();
            JSONObject jo = new JSONObject();
            jo.put("errcode", 9999);
            jo.put("errmsg", "Json mapper exception: "+e.toString());
            return jo;
        }
    }
}
