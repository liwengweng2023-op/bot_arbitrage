package co.codingnomads.bot.arbitrage.exchange;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.nio.ByteBuffer;

public class ExchangeWebSocketClient extends WebSocketClient {
    private final String exchangeName;
    private final Consumer<Object> messageHandler;
    private final URI serverUri;
    private ScheduledExecutorService reconnectExecutor;
    private ScheduledFuture<?> reconnectFuture;
    private boolean isReconnecting = false;
    private static final int RECONNECT_DELAY_SECONDS = 5;

    public ExchangeWebSocketClient(String exchangeName, URI serverUri, Consumer<Object> messageHandler) {
        super(serverUri);
        this.exchangeName = exchangeName;
        this.messageHandler = messageHandler;
        this.serverUri = serverUri;
        
        // 设置连接超时
        this.setConnectionLostTimeout(30); // 30秒连接超时
        
        // 初始化重连线程池
        this.reconnectExecutor = Executors.newSingleThreadScheduledExecutor();
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        System.out.println("[" + exchangeName + "] 连接已建立: " + serverUri);
        isReconnecting = false;
    }

    public String getExchangeName() {
        return exchangeName;
    }
    
    @Override
    public void onMessage(String message) {
        try {
            messageHandler.accept(message);
        } catch (Exception e) {
            System.err.println("[" + exchangeName + "] 处理文本消息时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    @Override
    public void onMessage(ByteBuffer bytes) {
        try {
            byte[] byteArray = new byte[bytes.remaining()];
            bytes.get(byteArray);
            messageHandler.accept(byteArray);
        } catch (Exception e) {
            System.err.println("[" + exchangeName + "] 处理二进制消息时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("[" + exchangeName + "] 连接已关闭: " + reason + " (code: " + code + ")");
        if (!isReconnecting) {
            scheduleReconnect();
        }
    }

    @Override
    public void onError(Exception ex) {
        System.err.println("[" + exchangeName + "] 连接错误: " + ex.getMessage());
        ex.printStackTrace();
        if (!isReconnecting) {
            scheduleReconnect();
        }
    }

    private synchronized void scheduleReconnect() {
        if (isReconnecting || (reconnectExecutor != null && reconnectExecutor.isShutdown())) {
            return;
        }
        
        isReconnecting = true;
        System.out.println("[" + exchangeName + "] " + RECONNECT_DELAY_SECONDS + "秒后尝试重新连接...");
        
        // 确保重连执行器已初始化
        if (reconnectExecutor == null) {
            reconnectExecutor = Executors.newSingleThreadScheduledExecutor();
        }
        
        // 取消之前的重连任务（如果存在）
        if (reconnectFuture != null && !reconnectFuture.isDone()) {
            reconnectFuture.cancel(false);
        }
        
        reconnectFuture = reconnectExecutor.schedule(() -> {
            try {
                if (isOpen()) {
                    close();
                }
                
                // 创建新的WebSocket连接
                System.out.println("[" + exchangeName + "] 正在重新连接到: " + serverUri);
                this.reconnectBlocking();
                
                // 重置重连状态
                isReconnecting = false;
                System.out.println("[" + exchangeName + "] 重新连接成功");
                
            } catch (Exception e) {
                System.err.println("[" + exchangeName + "] 重连失败: " + e.getMessage());
                // 重置重连状态，允许再次尝试
                isReconnecting = false;
                // 重新调度重连
                scheduleReconnect();
            }
        }, RECONNECT_DELAY_SECONDS, TimeUnit.SECONDS);
    }

}
