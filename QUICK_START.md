# 套利机器人快速启动指南

## 问题解决总结

已修复以下关键问题：

### 1. ✅ Log4j配置错误
- **问题**: 使用了不存在的`RollingFileAppender`类
- **修复**: 改为使用`DailyRollingFileAppender`
- **结果**: 日志系统现在可以正常工作

### 2. ✅ 数据库表结构缺失
- **问题**: Mapper文件缺少SQL语句，数据库表不存在
- **修复**: 创建了正确的数据库表和完整的Mapper SQL
- **结果**: 数据库操作现在可以正常执行

### 3. ✅ 控制台输出被禁用
- **问题**: 完全禁用了控制台输出，无法看到启动信息
- **修复**: 添加了控制台日志输出
- **结果**: 现在可以在控制台看到实时日志

## 启动步骤

### 1. 准备数据库
```sql
-- 在MySQL中执行
source src/main/mysql/create_tables.sql
```

### 2. 编译项目
```bash
mvn clean compile
```

### 3. 启动应用
```bash
# 方式1: 使用Maven启动
mvn spring-boot:run

# 方式2: 打包后启动
mvn clean package
java -jar target/bot.arbitrage-1.0-SNAPSHOT.jar

# 方式3: 使用批处理文件
test_startup.bat
```

## 预期输出

启动成功后，控制台应该显示：

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
10:30:45.134 [main] INFO  [RealTimeArbitrageService] - [统计] 检查次数: 0 | 处理: 0 | 跳过: 0 (0.0%) | 动态时间差阈值: 300ms
```

## 日志文件

- **主日志**: `log/arbitrage_bot.log`
- **错误日志**: `log/arbitrage_bot_error.log`
- **套利机会日志**: `log/arbitrage_opportunities.log`

## 监控信息

程序运行时会持续输出：
- WebSocket连接状态
- 价格数据更新
- 套利机会检测
- 统计信息（每分钟一次）

## 故障排除

如果仍有问题，请检查：

1. **数据库连接**: 确保MySQL服务运行，数据库`botarbitrage`存在
2. **网络连接**: 确保可以访问币安和火币API
3. **日志文件**: 查看`log/arbitrage_bot_error.log`中的错误信息
4. **Java版本**: 确保使用Java 8或更高版本

## 停止程序

按 `Ctrl+C` 停止程序，程序会优雅地关闭所有连接并保存最终统计信息。
