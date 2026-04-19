package com.g2rain.gateway.model.context;


import com.g2rain.common.web.ScopedContextHolder;

import java.util.concurrent.Callable;

/**
 *
 * @author alpha
 * @since 2025/10/6
 */
public final class EdgePrincipalContextHolder {
    /**
     * 当前线程的 {@link EdgePrincipalContext} 容器
     */
    private static final ScopedContextHolder<EdgePrincipalContext> HOLDER = ScopedContextHolder.create();

    private EdgePrincipalContextHolder() {
        // 私有构造，防止实例化
    }

    /**
     * 获取当前线程的 {@link EdgePrincipalContext}，如果不存在则抛出异常
     *
     * @return 当前线程的 {@link EdgePrincipalContext}
     * @throws IllegalStateException 如果上下文未绑定
     */
    public static EdgePrincipalContext require() {
        return HOLDER.require();
    }

    /**
     * 获取当前线程的 {@link EdgePrincipalContext}，如果不存在则返回null
     *
     * @return 当前线程的 {@link EdgePrincipalContext}
     * @throws IllegalStateException 如果上下文未绑定
     */
    public static EdgePrincipalContext get() {
        return HOLDER.get();
    }

    /**
     * 在指定 {@link EdgePrincipalContext} 作用域下执行 {@link Runnable} 任务
     *
     * @param ctx  线程上下文
     * @param task 待执行任务
     */
    public static void runWith(EdgePrincipalContext ctx, Runnable task) {
        HOLDER.runWith(ctx, task);
    }

    /**
     * 在指定 {@link EdgePrincipalContext} 作用域下执行 {@link Callable} 任务
     *
     * @param ctx  线程上下文
     * @param task 待执行任务
     * @param <T>  任务返回类型
     * @return 任务执行结果
     * @throws Exception 任务执行过程中可能抛出的异常
     */
    public static <T> T callWith(EdgePrincipalContext ctx, Callable<T> task) throws Exception {
        return HOLDER.callWith(ctx, task);
    }

    /**
     * 包装 {@link Runnable}，在执行时自动透传当前线程的 {@link EdgePrincipalContext}
     *
     * @param task 待包装任务
     * @return 包装后的任务
     */
    public static Runnable wrap(Runnable task) {
        return HOLDER.wrap(task);
    }

    /**
     * 包装 {@link Callable}，在执行时自动透传当前线程的 {@link EdgePrincipalContext}
     *
     * @param task 待包装任务
     * @param <T>  任务返回类型
     * @return 包装后的任务
     */
    public static <T> Callable<T> wrap(Callable<T> task) {
        return HOLDER.wrap(task);
    }
}
