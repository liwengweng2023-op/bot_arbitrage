-- 创建套利机器人所需的数据库表
CREATE DATABASE IF NOT EXISTS `botarbitrage` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
USE `botarbitrage`;

-- 套利机会表
CREATE TABLE IF NOT EXISTS `arbitrage_opportunities` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `symbol` varchar(20) NOT NULL COMMENT '交易对符号',
  `buy_exchange` varchar(50) NOT NULL COMMENT '买入交易所',
  `sell_exchange` varchar(50) NOT NULL COMMENT '卖出交易所',
  `buy_price` decimal(20,8) NOT NULL COMMENT '买入价格',
  `sell_price` decimal(20,8) NOT NULL COMMENT '卖出价格',
  `profit_margin` decimal(10,6) NOT NULL COMMENT '利润率(%)',
  `profit_amount` decimal(20,8) DEFAULT NULL COMMENT '利润金额',
  `detected_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '检测时间',
  PRIMARY KEY (`id`),
  KEY `idx_symbol` (`symbol`),
  KEY `idx_detected_at` (`detected_at`),
  KEY `idx_profit_margin` (`profit_margin`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='套利机会表';

-- 行情数据表
CREATE TABLE IF NOT EXISTS `market_data` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `exchange` varchar(50) NOT NULL COMMENT '交易所名称',
  `symbol` varchar(20) NOT NULL COMMENT '交易对符号',
  `bid_price` decimal(20,8) NOT NULL COMMENT '买一价',
  `ask_price` decimal(20,8) NOT NULL COMMENT '卖一价',
  `bid_volume` decimal(20,8) DEFAULT NULL COMMENT '买一量',
  `ask_volume` decimal(20,8) DEFAULT NULL COMMENT '卖一量',
  `timestamp` bigint(20) NOT NULL COMMENT '时间戳',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_exchange_symbol` (`exchange`, `symbol`),
  KEY `idx_timestamp` (`timestamp`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='行情数据表';

-- 插入测试数据
INSERT INTO `arbitrage_opportunities` (`symbol`, `buy_exchange`, `sell_exchange`, `buy_price`, `sell_price`, `profit_margin`, `detected_at`) 
VALUES ('ETHUSDT', '火币', '币安', 1234.56, 1235.78, 0.10, NOW());

INSERT INTO `market_data` (`exchange`, `symbol`, `bid_price`, `ask_price`, `timestamp`) 
VALUES ('币安', 'ETHUSDT', 1235.50, 1235.80, UNIX_TIMESTAMP() * 1000);

INSERT INTO `market_data` (`exchange`, `symbol`, `bid_price`, `ask_price`, `timestamp`) 
VALUES ('火币', 'ETHUSDT', 1234.50, 1234.80, UNIX_TIMESTAMP() * 1000);
