package com.hesong.weChatAdapter.runner;

import java.util.concurrent.BlockingQueue;

import com.hesong.jsonrpc.JsonrpcHandler;
import com.hesong.jsonrpc.WeChatMethodSet;
import com.hesong.smartbus.client.PackInfo;

public class JsonrpcHandlerRunner implements Runnable {

    private BlockingQueue<PackInfo> messageQueue;
    private BlockingQueue<PackInfo> responseQueue;
    private JsonrpcHandler handler;
    private int id;

    public JsonrpcHandlerRunner(BlockingQueue<PackInfo> messageQueue,
            BlockingQueue<PackInfo> responseQueue, int id) {
        super();
        this.messageQueue = messageQueue;
        this.responseQueue = responseQueue;
        this.handler = new JsonrpcHandler(new WeChatMethodSet());
        this.id = id;
        SmartbusExecutor.SmartbusLog.info("Handler number "+id+" loaded.");
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }


    public BlockingQueue<PackInfo> getMessageQueue() {
        return messageQueue;
    }

    public void setMessageQueue(BlockingQueue<PackInfo> messageQueue) {
        this.messageQueue = messageQueue;
    }

    public BlockingQueue<PackInfo> getResponseQueue() {
        return responseQueue;
    }

    public void setResponseQueue(BlockingQueue<PackInfo> responseQueue) {
        this.responseQueue = responseQueue;
    }

    public JsonrpcHandler getHandler() {
        return handler;
    }

    public void setHandler(JsonrpcHandler handler) {
        this.handler = handler;
    }

    @Override
    public void run() {
        while (true) {
            try {
                PackInfo pack = getMessageQueue().take();
                SmartbusExecutor.SmartbusLog
                        .info("New message in queue, handling...: "
                                + pack.toString());
                String response = handler.handle(pack.getText());
                if (response != null) {
                    pack.setText(response);
                    responseQueue.put(pack);
                }
            } catch (InterruptedException e) {
                SmartbusExecutor.SmartbusLog.info("MessageQueue exception: "
                        + e.toString());
            }
        }

    }

}