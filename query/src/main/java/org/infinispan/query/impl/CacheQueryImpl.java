package org.infinispan.query.impl;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.hibernate.search.filter.FullTextFilter;
import org.hibernate.search.exception.SearchException;
import org.hibernate.search.query.engine.spi.DocumentExtractor;
import org.hibernate.search.query.engine.spi.EntityInfo;
import org.hibernate.search.query.engine.spi.FacetManager;
import org.hibernate.search.query.engine.spi.HSQuery;
import org.hibernate.search.query.engine.spi.TimeoutExceptionFactory;
import org.hibernate.search.spi.SearchIntegrator;
import org.infinispan.AdvancedCache;
import org.infinispan.query.CacheQuery;
import org.infinispan.query.FetchOptions;
import org.infinispan.query.ResultIterator;
import org.infinispan.query.FetchOptions.FetchMode;
import org.infinispan.query.backend.KeyTransformationHandler;

/**
 * Implementation class of the CacheQuery interface.
 * <p/>
 *
 * @author Navin Surtani
 * @author Sanne Grinovero <sanne@hibernate.org> (C) 2011 Red Hat Inc.
 * @author Marko Luksa
 */
public class CacheQueryImpl implements CacheQuery {

   /**
    * Since CacheQuery extends {@link Iterable} it is possible to implicitly invoke
    * {@link #iterator()} in an "enhanced for loop".
    * When using the {@link FetchMode#LAZY} it is mandatory to close the {@link ResultIterator},
    * but users of the enhanced loop have no chance to invoke the method.
    * Therefore, it's important that the default fetch options use EAGER iteration.
    */
   private static final FetchOptions DEFAULT_FETCH_OPTIONS = new FetchOptions().fetchMode(FetchMode.EAGER);

   protected final AdvancedCache<?, ?> cache;
   protected final KeyTransformationHandler keyTransformationHandler;
   protected HSQuery hSearchQuery;
   private ProjectionConverter projectionConverter;

   public CacheQueryImpl(Query luceneQuery, SearchIntegrator searchFactory, AdvancedCache<?, ?> cache,
         KeyTransformationHandler keyTransformationHandler, Class<?>... classes) {
       this(luceneQuery, searchFactory, cache, keyTransformationHandler, null, classes);
   }

   public CacheQueryImpl(Query luceneQuery, SearchIntegrator searchFactory, AdvancedCache<?, ?> cache,
                         KeyTransformationHandler keyTransformationHandler, TimeoutExceptionFactory timeoutExceptionFactory,
                         Class<?>... classes) {
      this.keyTransformationHandler = keyTransformationHandler;
      this.cache = cache;
      hSearchQuery = searchFactory.createHSQuery();
      hSearchQuery
         .luceneQuery( luceneQuery )
         .targetedEntities( Arrays.asList( classes ) );

      if (timeoutExceptionFactory != null) {
         hSearchQuery.timeoutExceptionFactory( timeoutExceptionFactory );
      }
   }

   /**
    * Takes in a lucene filter and sets it to the filter field in the class.
    *
    * @param filter - lucene filter
    */
   @Override
   public CacheQuery filter(Filter filter) {
      hSearchQuery.filter(filter);
      return this;
   }

   /**
    * @return The result size of the query.
    */
   @Override
   public int getResultSize() {
      return hSearchQuery.queryResultSize();
   }

   @Override
   public CacheQuery sort(Sort sort) {
      hSearchQuery.sort(sort);
      return this;
   }

   /**
    * Enable a given filter by its name.
    *
    * @param name of filter.
    * @return a FullTextFilter object.
    */
   @Override
   public FullTextFilter enableFullTextFilter(String name) {
      return hSearchQuery.enableFullTextFilter(name);
   }

   /**
    * Disable a given filter by its name.
    *
    * @param name of filter.
    */
   @Override
   public CacheQuery disableFullTextFilter(String name) {
      hSearchQuery.disableFullTextFilter(name);
      return this;
   }

   /**
    * Sets the the result of the given integer value to the first result.
    *
    * @param firstResult index to be set.
    * @throws IllegalArgumentException if the index given is less than zero.
    */
   @Override
   public CacheQuery firstResult(int firstResult) {
      hSearchQuery.firstResult(firstResult);
      return this;
   }

   @Override
   public CacheQuery maxResults(int maxResults) {
      hSearchQuery.maxResults(maxResults);
      return this;
   }

   @Override
   public ResultIterator iterator() throws SearchException {
      return iterator(DEFAULT_FETCH_OPTIONS);
   }

   @Override
   public ResultIterator iterator(FetchOptions fetchOptions) throws SearchException {
      if (fetchOptions.getFetchMode() == FetchOptions.FetchMode.EAGER) {
         hSearchQuery.getTimeoutManager().start();
         List<EntityInfo> entityInfos = hSearchQuery.queryEntityInfos();
         return filterNulls(new EagerIterator(entityInfos, getResultLoader(), fetchOptions.getFetchSize()));
      } else if (fetchOptions.getFetchMode() == FetchOptions.FetchMode.LAZY) {
         DocumentExtractor extractor = hSearchQuery.queryDocumentExtractor();   //triggers actual Lucene search
         return filterNulls(new LazyIterator(extractor, getResultLoader(), fetchOptions.getFetchSize()));
      } else {
         throw new IllegalArgumentException("Unknown FetchMode " + fetchOptions.getFetchMode());
      }
   }

   private ResultIterator filterNulls(ResultIterator iterator) {
      return new NullFilteringResultIterator(iterator);
   }

   @Override
   public List<Object> list() throws SearchException {
      hSearchQuery.getTimeoutManager().start();
      final List<EntityInfo> entityInfos = hSearchQuery.queryEntityInfos();
      return getResultLoader().load(entityInfos);
   }

   private QueryResultLoader getResultLoader() {
      return isProjected() ? getProjectionLoader() : getEntityLoader();
   }

   private boolean isProjected() {
      return hSearchQuery.getProjectedFields() != null;
   }

   private ProjectionLoader getProjectionLoader() {
      return new ProjectionLoader(projectionConverter, getEntityLoader());
   }

   private EntityLoader getEntityLoader() {
      return new EntityLoader(cache, keyTransformationHandler);
   }

   @Override
   public FacetManager getFacetManager() {
      return hSearchQuery.getFacetManager();
   }

   @Override
   public Explanation explain(int documentId) {
      return hSearchQuery.explain(documentId);
   }

   @Override
   public CacheQuery projection(String... fields) {
      this.projectionConverter = new ProjectionConverter(fields, cache, keyTransformationHandler);
      hSearchQuery.projection(projectionConverter.getHSearchProjection());
      return this;
   }

   @Override
   public CacheQuery timeout(long timeout, TimeUnit timeUnit) {
      hSearchQuery.getTimeoutManager().setTimeout(timeout, timeUnit);
      return this;
   }

}
