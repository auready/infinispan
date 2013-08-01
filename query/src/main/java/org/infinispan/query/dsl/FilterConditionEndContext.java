package org.infinispan.query.dsl;

import java.util.Collection;

/**
 * @author anistor@redhat.com
 * @since 6.0
 */
public interface FilterConditionEndContext {

   FilterConditionContext in(Object... value);

   FilterConditionContext in(Collection values);

   FilterConditionContext like(String pattern);

   FilterConditionContext contains(Object value);

   FilterConditionContext containsAll(Object... value);

   FilterConditionContext containsAll(Collection values);

   FilterConditionContext containsAny(Object... value);

   FilterConditionContext containsAny(Collection values);

   FilterConditionContext isNull();

   FilterConditionContext eq(Object value);

   FilterConditionContext lt(Object value);

   FilterConditionContext lte(Object value);

   FilterConditionContext gt(Object value);

   FilterConditionContext gte(Object value);

   RangeConditionContext between(Object from, Object to);
}
