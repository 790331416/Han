package com.han.system.controller.admin;

import com.baomidou.mybatisplus.annotation.TableField;
import com.han.system.domain.po.SysPostPo;
import com.han.system.sdfz.education.EducationMasterDataController;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisServerCommands;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ASysMonitorRegressionTest {

    @Test
    void monitorResponsesMatchTheManagementUiContract() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisCallback.class))).thenAnswer(invocation -> {
            RedisConnection connection = mock(RedisConnection.class);
            RedisServerCommands commands = mock(RedisServerCommands.class);
            Properties info = new Properties();
            info.setProperty("redis_version", "7.2.0");
            info.setProperty("connected_clients", "2");
            when(connection.serverCommands()).thenReturn(commands);
            when(commands.info()).thenReturn(info);
            when(commands.dbSize()).thenReturn(3L);
            return invocation.<RedisCallback<Map<String, Object>>>getArgument(0).doInRedis(connection);
        });
        @SuppressWarnings("unchecked") Cursor<String> cursor = mock(Cursor.class);
        when(cursor.hasNext()).thenReturn(true, false);
        when(cursor.next()).thenReturn("han:token:1");
        when(redis.scan(any())).thenReturn(cursor);
        when(redis.getExpire("han:token:1", TimeUnit.SECONDS)).thenReturn(120L);

        ASysMonitorController controller = new ASysMonitorController(redis);
        assertThat(controller.server().getData()).containsKeys("jvm", "sys");
        assertThat(controller.cache().getData()).containsEntry("redisVersion", "7.2.0").containsEntry("dbSize", 3L);
        assertThat(controller.cacheKeys("han:*").getData()).containsExactly(Map.of("key", "han:token:1", "ttl", 120L));
    }

    @Test
    void postSortAndExcelCommentsUseTheProductionSchemaAndDistinctCells() throws Exception {
        TableField column = SysPostPo.class.getDeclaredField("postSort").getAnnotation(TableField.class);
        assertThat(column).isNotNull();
        assertThat(column.value()).isEqualTo("sort");

        Method cellComment = EducationMasterDataController.class.getDeclaredMethod(
                "cellComment", org.apache.poi.ss.usermodel.Workbook.class, Drawing.class, int.class, String.class);
        cellComment.setAccessible(true);
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("人员导入");
            Drawing<?> drawing = sheet.createDrawingPatriarch();
            Comment first = (Comment) cellComment.invoke(null, workbook, drawing, 0, "学校说明");
            Comment second = (Comment) cellComment.invoke(null, workbook, drawing, 1, "姓名说明");
            sheet.createRow(0).createCell(0).setCellComment(first);
            sheet.getRow(0).createCell(1).setCellComment(second);
            workbook.write(output);
            assertThat(output.size()).isPositive();
            assertThat((int) first.getClientAnchor().getCol1()).isZero();
            assertThat((int) second.getClientAnchor().getCol1()).isEqualTo(1);
        }
    }
}
