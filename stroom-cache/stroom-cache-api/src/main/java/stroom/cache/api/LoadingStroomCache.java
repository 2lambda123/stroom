package stroom.cache.api;

import java.util.Optional;
import java.util.function.Function;

public interface LoadingStroomCache<K, V> extends StroomCache<K, V> {

    /**
     * Gets the value associated with key from the cache. If key is not found in the cache
     * then the loadFunction will be called to load the entry into the cache.
     * @return The value associated with key or null if the loadFunction returns null.
     */
    @Override
    V get(K key);

    /**
     * Same behaviour as {@link LoadingStroomCache#get(Object)} except the results is wrapped
     * in an {@link Optional}
     * @return The value associated with key or an empty {@link Optional} if the
     * loadFunction returns null.
     */
    @Override
    Optional<V> getOptional(K key);

    /**
     * Gets the value associated with key from the cache. If key is not found in the cache
     * then valueProvider will be called to load the entry into the cache.
     * valueProvider overrides loadFunction so loadFunction will NOT be called.
     * @return The value associated with key or null if the valueProvider returns null.
     */
    @Override
    V get(K key, Function<K, V> valueProvider);

    /**
     * If key exists in the cache returns an {@link Optional} containing the value
     * associated with key, else returns an empty optional.
     * It will NOT call the loadFunction.
     */
    Optional<V> getIfPresent(K key);
}