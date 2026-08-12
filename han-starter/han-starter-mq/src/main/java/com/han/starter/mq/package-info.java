/**
 * 消息队列 Starter —— <b>只有接口定义，没有实现，且全仓无人依赖</b>。
 *
 * <h2>实际状态</h2>
 * <ul>
 *   <li>本包唯一的源文件是 {@link com.han.starter.mq.MQProvider} 接口：没有实现类，
 *       没有 {@code @AutoConfiguration}，也没有
 *       {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}。
 *       <b>容器里不会有 {@code MQProvider} 类型的 Bean</b>，
 *       {@code @Autowired MQProvider} 会以 {@code NoSuchBeanDefinitionException} 直接启动失败。</li>
 *   <li>全仓搜 {@code MQProvider} 只命中接口定义自身，零使用；也没有任何模块依赖本 starter。</li>
 *   <li>{@code pom.xml} 里的 {@code spring-boot-starter-amqp} 与 {@code spring-kafka} 都是
 *       {@code optional}，不会传递。</li>
 * </ul>
 *
 * <h2>为什么保留而不是删掉</h2>
 * <p>仓库规范要求未经授权不得删除已有模块。本模块无人依赖，删除的连带影响最小
 * （只涉及 {@code han-starter/pom.xml} 的 modules 列表），是三个空壳里最适合直接删的一个，
 * 但仍需授权后执行。在此之前先显式标注状态，避免被当成既有能力复用。
 *
 * <h2>要落地需要做什么</h2>
 * <ol>
 *   <li>先确认真实需求（当前平台的异步都走 Quartz 与本地事件，没有 MQ 场景），</li>
 *   <li>补 RabbitMQ 或 Kafka 实现，用 {@code @ConditionalOnClass} 守住 optional 依赖，</li>
 *   <li>补 {@code @AutoConfiguration} 与 {@code AutoConfiguration.imports}。</li>
 * </ol>
 */
package com.han.starter.mq;
