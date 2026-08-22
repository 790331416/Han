package com.han.open.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.han.open.domain.po.OpenApiResourcePo;
import com.han.open.mapper.OpenApiResourceMapper;
import com.han.open.service.ResourcePathMappingService;
import com.han.common.tenant.annotation.IgnoreTenant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;
import jakarta.annotation.PostConstruct;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 资源路径映射服务实现
 */
@Service
@RequiredArgsConstructor
public class ResourcePathMappingServiceImpl implements ResourcePathMappingService {

    private final OpenApiResourceMapper resourceMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /** 只在完整快照构建成功后替换，读请求无需加锁。 */
    private volatile List<OpenApiResourcePo> resourceSnapshot = List.of();

    @PostConstruct
    public void init() {
        refreshCache();
    }

    @Override
    public OpenApiResourcePo matchResource(String httpMethod, String path) {
        if (httpMethod == null || path == null || path.isBlank()) {
            return null;
        }

        String method = httpMethod.trim().toUpperCase(Locale.ROOT);
        Comparator<String> patternComparator = pathMatcher.getPatternComparator(path);
        return resourceSnapshot.stream()
                .filter(resource -> resource.getHttpMethod() != null
                        && method.equalsIgnoreCase(resource.getHttpMethod().trim()))
                .filter(resource -> resource.getPath() != null && pathMatcher.match(resource.getPath(), path))
                .min((left, right) -> compareMatches(left, right, path, patternComparator))
                .orElse(null);
    }

    @Override
    @IgnoreTenant
    public void refreshCache() {
        List<OpenApiResourcePo> resources = resourceMapper.selectList(new LambdaQueryWrapper<OpenApiResourcePo>()
                .eq(OpenApiResourcePo::getStatus, 0));
        List<OpenApiResourcePo> nextSnapshot = resources == null ? List.of() : resources.stream()
                .filter(Objects::nonNull)
                .filter(resource -> Objects.equals(resource.getStatus(), 0))
                .toList();
        resourceSnapshot = List.copyOf(nextSnapshot);
    }

    private int compareMatches(OpenApiResourcePo left, OpenApiResourcePo right, String path,
                               Comparator<String> patternComparator) {
        boolean leftExact = path.equals(left.getPath());
        boolean rightExact = path.equals(right.getPath());
        if (leftExact != rightExact) {
            return leftExact ? -1 : 1;
        }

        int specificity = patternComparator.compare(left.getPath(), right.getPath());
        if (specificity != 0) {
            return specificity;
        }

        int pathOrder = compareNullable(left.getPath(), right.getPath());
        if (pathOrder != 0) {
            return pathOrder;
        }
        int idOrder = compareNullable(left.getId(), right.getId());
        if (idOrder != 0) {
            return idOrder;
        }
        int codeOrder = compareNullable(left.getResourceCode(), right.getResourceCode());
        return codeOrder != 0 ? codeOrder : compareNullable(left.getResourceName(), right.getResourceName());
    }

    private static <T extends Comparable<? super T>> int compareNullable(T left, T right) {
        if (left == right) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        return left.compareTo(right);
    }
}
