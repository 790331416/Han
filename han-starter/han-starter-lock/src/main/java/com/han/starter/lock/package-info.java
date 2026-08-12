/**
 * 分布式锁 Starter —— <b>只有接口定义，没有实现</b>。
 *
 * <h2>实际状态</h2>
 * <ul>
 *   <li>本包唯一的源文件是 {@link com.han.starter.lock.LockProvider} 接口：没有实现类，
 *       没有 {@code @AutoConfiguration}，也没有
 *       {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}。
 *       <b>容器里不会有 {@code LockProvider} 类型的 Bean</b>，
 *       {@code @Autowired LockProvider} 会以 {@code NoSuchBeanDefinitionException} 直接启动失败。</li>
 *   <li>全仓搜 {@code LockProvider} 只命中接口定义自身，零使用。</li>
 *   <li>{@code han-modules/han-job} 的 pom 以注释「分布式锁 Starter」引入了本模块却从未用过；
 *       它的调度互斥实际是靠 {@code han-common-redis} 自己实现的。
 *       换句话说：<b>依赖存在不代表 han-job 有分布式互斥保护</b>，排查调度重复执行问题时
 *       不要被这条依赖误导。</li>
 *   <li>{@code pom.xml} 里的 {@code redisson-spring-boot-starter} 是 {@code optional}，不会传递。</li>
 * </ul>
 *
 * <h2>为什么保留而不是删掉</h2>
 * <p>仓库规范要求未经授权不得删除已有模块与依赖。这里先把「有接口不等于有能力」显式记下来，
 * 去留由后续统一决策。
 *
 * <h2>要落地需要做什么</h2>
 * <ol>
 *   <li>补 {@code RedissonLockProvider}，用 {@code @ConditionalOnClass(RedissonClient.class)}
 *       与 {@code @ConditionalOnBean(RedissonClient.class)} 守住 optional 依赖，</li>
 *   <li>补 {@code @AutoConfiguration} 与 {@code AutoConfiguration.imports}，</li>
 *   <li>han-job 显式引入 redisson（optional 依赖不传递，只加本模块依赖仍然拿不到 Bean），
 *       并把调度互斥切过来。</li>
 * </ol>
 */
package com.han.starter.lock;
