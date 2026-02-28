package com.han.common.core.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

@Data
@EqualsAndHashCode(callSuper = true)
public class TenantQuery extends BaseQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long tenantId;
}
