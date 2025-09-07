package co.codingnomads.bot.arbitrage.exchange;

import org.apache.log4j.Logger;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.nio.ByteBuffer;

/**
 * 交易所WebSocket客户端基类
 * 
 * 该基类提供：
 * 1. WebSocket连接管理
 * 2. 自动重连机制
 * 3. 消息处理回调
 * 4. 连接状态监控
 * 5. 错误处理和日志记录
 * 
 * 子类需要实现具体的消息处理逻辑
 * 
 * @author CodingNomads
 * @version 1.0
 * @since 2024
 */
public class ExchangeWebSocketClient extends WebSocketClient {
    
    // ==================== 日志记录器 ====================
    private static final Logger logger = Logger.getLogger(ExchangeWebSocketClient.class);
    
    // ==================== 配置常量 ====================
    /** 重连延迟时间（秒） */
    private static final int RECONNECT_DELAY_SECONDS = 5;
    
    /** 连接超时时间（秒） */
    private static final int CONNECTION_TIMEOUT_SECONDS = 30;
    
    // ==================== 实例变量 ====================
    /** 交易所名称 */
    private final String exchangeName;
    
    /** 消息处理器 */
    private final Consumer<Object> messageHandler;
    
    /** 服务器URI */
    private final URI serverUri;
    
    /** 重连执行器 */
    private ScheduledExecutorService reconnectExecutor;
    
    /** 重连任务 */
    private ScheduledFuture<?> reconnectFuture;
    
    /** 是否正在重连 */
    private boolean isReconnecting = false;

    /**
     * 构造函数
     * 
     * @param exchangeName 交易所名称
     * @param serverUri WebSocket服务器URI
     * @param messageHandler 消息处理器
     */
    public ExchangeWebSocketClient(String exchangeName, URI serverUri, Consumer<Object> messageHandler) {
        super(serverUri);
        this.exchangeName = exchangeName;
        this.messageHandler = messageHandler;
        this.serverUri = serverUri;
        
        // 设置连接超时
        this.setConnectionLostTimeout(CONNECTION_TIMEOUT_SECONDS);
        
        // 初始化重连线程池
        this.reconnectExecutor = Executors.newSingleThreadScheduledExecutor();
    }

    // ==================== 连接生命周期方法 ====================
    
    /**
     * 连接建立时的回调
     * 
     * @param handshakedata 握手数据
     */
    @Override
    public void onOpen(ServerHandshake handshakedata) {
        logger.info("[" + exchangeName + "] 连接已建立: " + serverUri);
        isReconnecting = false;
    }
    
    /**
     * 获取交易所名称
     * 
     * @return 交易所名称
     */
    public String getExchangeName() {
        return exchangeName;
    }
    
    // ==================== 消息处理方法 ====================
    
    /**
     * 处理文本消息
     * 
     * @param message 文本消息
     */
    @Override
    public void onMessage(String message) {
        try {
            messageHandler.accept(message);
        } catch (Exception e) {
            logError("处理文本消息时出错", e);
        }
    }
    
    /**
     * 处理二进制消息
     * 
     * @param bytes 二进制数据
     */
    @Override
    public void onMessage(ByteBuffer bytes) {
        try {
            byte[] byteArray = new byte[bytes.remaining()];
            bytes.get(byteArray);
            messageHandler.accept(byteArray);
        } catch (Exception e) {
            logError("处理二进制消息时出错", e);
        }
    }

    /**
     * 连接关闭时的回调
     * 
     * @param code 关闭代码
     * @param reason 关闭原因
     * @param remote 是否由远程端关闭
     */
    @Override
    public void onClose(int code, String reason, boolean remote) {
        logger.warn("[" + exchangeName + "] 连接已关闭: " + reason + " (code: " + code + ")");
        if (!isReconnecting) {
            scheduleReconnect();
        }
    }

    /**
     * 连接错误时的回调
     * 
     * @param ex 异常对象
     */
    @Override
    public void onError(Exception ex) {
        logError("连接错误", ex);
        if (!isReconnecting) {
            scheduleReconnect();
        }
    }

    // ==================== 重连管理方法 ====================
    
    /**
     * 调度重连任务
     * 在连接断开或出错时自动重连
     */
    private synchronized void scheduleReconnect() {
        if (isReconnecting || (reconnectExecutor != null && reconnectExecutor.isShutdown())) {
            return;
        }
        
        isReconnecting = true;
        logger.info("[" + exchangeName + "] " + RECONNECT_DELAY_SECONDS + "秒后尝试重新连接...");
        
        // 确保重连执行器已初始化
        initializeReconnectExecutor();
        
        // 取消之前的重连任务（如果存在）
        cancelExistingReconnectTask();
        
        // 调度新的重连任务
        scheduleNewReconnectTask();
    }
    
    /**
     * 初始化重连执行器
     */
    private void initializeReconnectExecutor() {
        if (reconnectExecutor == null) {
            reconnectExecutor = Executors.newSingleThreadScheduledExecutor();
        }
    }
    
    /**
     * 取消现有的重连任务
     */
    private void cancelExistingReconnectTask() {
        if (reconnectFuture != null && !reconnectFuture.isDone()) {
            reconnectFuture.cancel(false);
        }
    }
    
    /**
     * 调度新的重连任务
     */
    private void scheduleNewReconnectTask() {
        reconnectFuture = reconnectExecutor.schedule(() -> {
            try {
                performReconnect();
            } catch (Exception e) {
                handleReconnectFailure(e);
            }
        }, RECONNECT_DELAY_SECONDS, TimeUnit.SECONDS);
    }
    
    /**
     * 执行重连操作
     * 
     * @throws Exception 重连异常
     */
    private void performReconnect() throws Exception {
        if (isOpen()) {
            close();
        }
        
        // 创建新的WebSocket连接
        logger.info("[" + exchangeName + "] 正在重新连接到: " + serverUri);
        this.reconnectBlocking();
        
        // 重置重连状态
        isReconnecting = false;
        logger.info("[" + exchangeName + "] 重新连接成功");
    }
    
    /**
     * 处理重连失败
     * 
     * @param e 异常对象
     */
    private void handleReconnectFailure(Exception e) {
        logger.error("[" + exchangeName + "] 重连失败: " + e.getMessage(), e);
        // 重置重连状态，允许再次尝试
        isReconnecting = false;
        // 重新调度重连
        scheduleReconnect();
    }
    
    // ==================== 工具方法 ====================
    
    /**
     * 记录错误日志
     * 
     * @param message 错误消息
     * @param e 异常对象
     */
    private void logError(String message, Exception e) {
        logger.error("[" + exchangeName + "] " + message + ": " + e.getMessage(), e);
    }

}
