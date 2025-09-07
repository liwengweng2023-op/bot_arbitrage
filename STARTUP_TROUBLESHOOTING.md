# 套利机器人启动问题诊断指南

## 问题描述
程序启动时报错，且无持续的市场监控信息输出。

## 已修复的问题

### 1. Log4j配置问题
**问题**: 使用了不存在的`org.apache.log4j.rolling.RollingFileAppender`类
**修复**: 改为使用`org.apache.log4j.DailyRollingFileAppender`
**影响**: 修复了日志配置错误导致的启动失败

### 2. 控制台输出问题
**问题**: 完全禁用了控制台输出，无法看到启动信息
**修复**: 添加了控制台日志输出，同时保留文件日志
**影响**: 现在可以在控制台看到启动和运行信息

### 3. Application.java中的System.out.println
**问题**: 仍有System.out.println未替换为log4j
**修复**: 全部替换为logger.info()
**影响**: 统一了日志输出方式

## 启动步骤

### 1. 编译项目
```bash
mvn clean compile
```

### 2. 测试数据库连接
```bash
# 运行测试启动类
mvn exec:java -Dexec.mainClass="co.codingnomads.bot.arbitrage.TestStartup"
```

### 3. 启动完整应用
```bash
# 方式1: 使用Maven
mvn spring-boot:run

# 方式2: 打包后运行
mvn clean package
java -jar target/bot.arbitrage-1.0-SNAPSHOT.jar

# 方式3: 使用批处理文件
test_startup.bat
```

## 日志文件位置
- 主日志: `log/arbitrage_bot.log`
- 错误日志: `log/arbitrage_bot_error.log`
- 套利机会日志: `log/arbitrage_opportunities.log`

## 常见问题排查

### 1. 数据库连接失败
**症状**: 启动时报数据库连接错误
**解决**: 
- 检查MySQL服务是否运行
- 验证数据库连接参数
- 确认数据库`botarbitrage`存在

### 2. WebSocket连接失败
**症状**: 无法连接到币安或火币API
**解决**:
- 检查网络连接
- 验证防火墙设置
- 确认API地址可访问

### 3. 日志文件权限问题
**症状**: 无法创建日志文件
**解决**:
- 确保`log/`目录存在且有写权限
- 检查磁盘空间

## 预期输出
启动成功后应该看到：
```
10:30:45.123 [main] INFO  [Application] - === 启动简化版套利监控服务 ===
10:30:45.124 [main] INFO  [Application] - 监控交易所: 币安(Binance) + 火币(Huobi)
10:30:45.125 [main] INFO  [Application] - 监控交易对: ETH/USDT
10:30:45.126 [main] INFO  [Application] - 套利阈值: 0.03%
10:30:45.127 [main] INFO  [Application] - 数据保存: MySQL数据库
10:30:45.128 [main] INFO  [Application] - 按 Ctrl+C 停止监控
10:30:45.129 [main] INFO  [Application] - =====================================
10:30:45.130 [main] INFO  [RealTimeArbitrageService] - [系统] 套利监控服务已启动，等待WebSocket连接...
10:30:45.131 [main] INFO  [RealTimeArbitrageService] - [系统] 套利服务已启动，开始监控 ETHUSDT 交易对...
10:30:45.132 [WebSocket-1] INFO  [RealTimeArbitrageService] - [Binance] 连接已建立，开始接收数据...
10:30:45.133 [WebSocket-2] INFO  [RealTimeArbitrageService] - [Huobi] 连接已建立，发送订阅请求...
```

## 下一步
如果仍有问题，请检查：
1. 日志文件中的具体错误信息
2. 数据库连接状态
3. 网络连接状态
4. Java版本兼容性
