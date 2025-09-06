-- 简化版套利监控数据库结构
CREATE DATABASE IF NOT EXISTS `botarbitrage` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
USE `botarbitrage`;

-- 行情数据表 - 存储所有接收到的行情信息
DROP TABLE IF EXISTS `market_data`;
CREATE TABLE `market_data` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `exchange` varchar(20) NOT NULL COMMENT '交易所名称',
  `symbol` varchar(20) NOT NULL COMMENT '交易对符号',
  `bid_price` decimal(20,8) NOT NULL COMMENT '买一价',
  `ask_price` decimal(20,8) NOT NULL COMMENT '卖一价',
  `bid_volume` decimal(20,8) DEFAULT NULL COMMENT '买一量',
  `ask_volume` decimal(20,8) DEFAULT NULL COMMENT '卖一量',
  `timestamp` bigint(20) NOT NULL COMMENT '数据时间戳',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_exchange_symbol` (`exchange`, `symbol`),
  KEY `idx_timestamp` (`timestamp`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='行情数据表';

-- 套利机会表 - 存储检测到的套利机会
DROP TABLE IF EXISTS `arbitrage_opportunities`;
CREATE TABLE `arbitrage_opportunities` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `symbol` varchar(20) NOT NULL COMMENT '交易对符号',
  `buy_exchange` varchar(20) NOT NULL COMMENT '买入交易所',
  `sell_exchange` varchar(20) NOT NULL COMMENT '卖出交易所',
  `buy_price` decimal(20,8) NOT NULL COMMENT '买入价格',
  `sell_price` decimal(20,8) NOT NULL COMMENT '卖出价格',
  `profit_margin` decimal(10,6) NOT NULL COMMENT '利润率(%)',
  `profit_amount` decimal(20,8) DEFAULT NULL COMMENT '预期利润金额',
  `detected_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '检测时间',
  PRIMARY KEY (`id`),
  KEY `idx_symbol` (`symbol`),
  KEY `idx_detected_at` (`detected_at`),
  KEY `idx_profit_margin` (`profit_margin`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='套利机会表';

-- 系统统计表 - 存储系统运行统计信息
DROP TABLE IF EXISTS `system_stats`;
CREATE TABLE `system_stats` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `stat_date` date NOT NULL COMMENT '统计日期',
  `total_market_data` int(11) DEFAULT 0 COMMENT '总行情数据条数',
  `binance_data_count` int(11) DEFAULT 0 COMMENT '币安数据条数',
  `huobi_data_count` int(11) DEFAULT 0 COMMENT '火币数据条数',
  `arbitrage_opportunities` int(11) DEFAULT 0 COMMENT '套利机会数量',
  `max_profit_margin` decimal(10,6) DEFAULT 0 COMMENT '最大利润率',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_stat_date` (`stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='系统统计表';
