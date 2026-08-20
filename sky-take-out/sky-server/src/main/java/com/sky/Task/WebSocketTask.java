package com.sky.Task;

import com.sky.websocket.WebSocketServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class WebSocketTask {
    @Autowired
    private WebSocketServer webSocketServer;

    /**
     * 通过WebSocket每隔5秒向客户端发送消息
     * 该广播为纯文本测试消息，会导致管理端 JSON.parse 报错，正式环境已停用；
     * 需要测试服务端推送时可取消注释
     */
    // @Scheduled(cron = "0/5 * * * * ?")
    // public void sendMessageToClient() {
    //     webSocketServer.sendToAllClient("这是来自服务端的消息：" + DateTimeFormatter.ofPattern("HH:mm:ss").format(LocalDateTime.now()));
    // }
}
