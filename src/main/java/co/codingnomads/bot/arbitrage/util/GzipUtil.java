package co.codingnomads.bot.arbitrage.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;

/**
 * GZIP解压工具类
 */
public class GzipUtil {

    private static final Logger logger = LoggerFactory.getLogger(GzipUtil.class);

    /**
     * 解压GZIP数据
     *
     * @param compressed 压缩的字节数组
     * @return 解压后的字符串，如果解压失败返回空字符串
     */
    public static String decompressGzip(byte[] compressed) {
        if (compressed == null || compressed.length == 0) {
            return "";
        }

        try (ByteArrayInputStream bis = new ByteArrayInputStream(compressed);
             GZIPInputStream gzipIn = new GZIPInputStream(bis);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzipIn.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
            return out.toString("UTF-8");
        } catch (IOException e) {
            logger.error("解压GZIP数据时出错: " + e.getMessage(), e);
            return "";
        }
    }
}