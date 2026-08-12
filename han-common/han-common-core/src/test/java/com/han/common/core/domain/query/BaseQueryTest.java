package com.han.common.core.domain.query;

import com.han.common.core.domain.PageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分页参数无上限校验时，客户端传 pageSize=10000000 就能把整表拖出来，
 * pageNum 传 0 或负数还会算出负偏移量。
 */
class BaseQueryTest {

    @Test
    @DisplayName("pageSize 超过上限时被钳制，不再能一次拉全表")
    void clampsOversizedPageSize() {
        BaseQuery query = new BaseQuery();
        query.setPageSize(10_000_000);

        assertEquals(BaseQuery.MAX_PAGE_SIZE, query.getSafePageSize());
    }

    @Test
    @DisplayName("pageNum / pageSize 非法时回落到默认值，offset 不会为负")
    void neverProducesNegativeOffset() {
        BaseQuery query = new BaseQuery();
        query.setPageNum(0);
        assertTrue(query.getOffset() >= 0);

        query.setPageNum(-5);
        assertTrue(query.getOffset() >= 0);

        query.setPageNum(null);
        query.setPageSize(null);
        assertEquals(0, query.getOffset());
        assertEquals(1, query.getSafePageNum());
        assertEquals(10, query.getSafePageSize());
    }

    @Test
    @DisplayName("合法分页参数按原值计算偏移")
    void keepsValidPaging() {
        BaseQuery query = new BaseQuery();
        query.setPageNum(3);
        query.setPageSize(20);

        assertEquals(40, query.getOffset());
    }

    @Test
    @DisplayName("PageResult 从查询对象取分页参数后能算出总页数")
    void pageResultFillsPagingMetadata() {
        BaseQuery query = new BaseQuery();
        query.setPageNum(2);
        query.setPageSize(20);

        PageResult<String> result = PageResult.of(List.of("a"), 45L, query);

        assertEquals(2, result.getPageNum());
        assertEquals(20, result.getPageSize());
        assertEquals(3, result.getPages());
    }
}
