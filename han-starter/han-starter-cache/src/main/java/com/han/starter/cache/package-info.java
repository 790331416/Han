/**
 * 缓存 Starter —— <b>当前是能力占位，没有任何实现</b>。
 *
 * <h2>实际状态</h2>
 * <ul>
 *   <li>本包在本文件之前<b>一行代码都没有</b>：没有 {@code CacheProvider} 接口，没有实现类，
 *       没有 {@code @AutoConfiguration}，也没有
 *       {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}。
 *       构建产物是一个空 jar。</li>
 *   <li>{@code pom.xml} 里声明的 {@code spring-boot-starter-data-redis} 与 {@code caffeine}
 *       都是 {@code optional}，<b>不会传递</b>给依赖方。</li>
 *   <li>{@code han-modules/han-system} 与 {@code han-modules/han-tenant} 的 pom 都依赖了本模块，
 *       但实际什么也得不到 —— 依赖图上看着「缓存能力已经收口」，代码里并没有。
 *       两个模块真正在用的是 {@code han-common-redis}。</li>
 * </ul>
 *
 * <h2>为什么保留而不是删掉</h2>
 * <p>仓库规范要求未经授权不得删除已有模块与依赖。删除本模块要同时改
 * {@code han-starter/pom.xml} 的 modules 列表、根 pom，以及 han-system / han-tenant 两个业务
 * 模块的依赖声明，跨了三个改动范围。这里先把「名不副实」这一点显式记下来，
 * 消除误导，去留由后续统一决策。
 *
 * <h2>要落地需要做什么</h2>
 * <ol>
 *   <li>定义 {@code CacheProvider} 抽象（get / put / evict / TTL），</li>
 *   <li>补 Caffeine 与 Redis 两个实现，各自用 {@code @ConditionalOnClass} 守住 optional 依赖，</li>
 *   <li>补 {@code @AutoConfiguration} 与 {@code AutoConfiguration.imports}，</li>
 *   <li>让 han-system / han-tenant 真正改用它，否则依赖仍然是纯装饰。</li>
 * </ol>
 * <p>在第 4 步之前不要急着加实现：只有抽象没有消费方，等于再造一层没人用的间接。
 */
package com.han.starter.cache;
