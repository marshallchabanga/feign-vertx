package feign;

import feign.InvocationHandlerFactory.MethodHandler;
import feign.vertx.VertxHttpClient;
import io.vertx.core.Future;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;

/**
 * {@link InvocationHandler} implementation that transforms calls to methods of feign contract into
 * asynchronous HTTP requests via vertx.
 *
 * @author Alexei KLENIN
 */
final class VertxInvocationHandler implements InvocationHandler {
  private final Target<?> target;
  private final Map<Method, MethodHandler> dispatch;

  private VertxInvocationHandler(
      final Target<?> target,
      final Map<Method, MethodHandler> dispatch) {
    this.target = target;
    this.dispatch = dispatch;
  }

  @Override
  public Object invoke(final Object proxy, final Method method, final Object[] args) {
    switch (method.getName()) {
      case "equals" :
        final Object otherHandler = args.length > 0 && args[0] != null
            ? Proxy.getInvocationHandler(args[0])
            : null;
        return equals(otherHandler);
      case "hashCode" :
        return hashCode();
      case "toString":
        return toString();
      default :
        if (isReturnsFuture(method)) {
          return invokeRequestMethod(method, args);
        } else {
          throw new FeignException(String.format(
              "Method %s of contract %s doesn't return io.vertx.core.Future",
              method.getName(),
              method.getDeclaringClass().getSimpleName()));
        }
    }
  }

  /**
   * Transforms method invocation into request that executed by {@link VertxHttpClient}.
   *
   * @param method  invoked method
   * @param args  provided arguments to method
   *
   * @return future with decoded result or occurred exception
   */
  private Future invokeRequestMethod(final Method method, final Object[] args) {
    try {
      return (Future) dispatch.get(method).invoke(args);
    } catch (Throwable throwable) {
      return Future.failedFuture(throwable);
    }
  }

  /**
   * Checks if method must return vertx {@code Future}.
   *
   * @param method  invoked method
   *
   * @return true if method must return Future, false if not
   */
  private boolean isReturnsFuture(final Method method) {
    return Future.class.isAssignableFrom(method.getReturnType());
  }

  @Override
  public boolean equals(final Object other) {
    if (other instanceof VertxInvocationHandler) {
      final VertxInvocationHandler otherHandler = (VertxInvocationHandler) other;
      return this.target.equals(otherHandler.target);
    }

    return false;
  }

  @Override
  public int hashCode() {
    return target.hashCode();
  }

  @Override
  public String toString() {
    return target.toString();
  }

  /**
   * Factory for VertxInvocationHandler.
   */
  static final class Factory implements InvocationHandlerFactory {

    @Override
    public InvocationHandler create(
        final Target target,
        final Map<Method, MethodHandler> dispatch) {
      return new VertxInvocationHandler(target, dispatch);
    }
  }
}
