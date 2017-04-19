package org.wikidata.query.rdf.blazegraph.mwapi;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http.HttpMethod;
import org.openrdf.model.Literal;
import org.openrdf.model.impl.LiteralImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wikidata.query.rdf.blazegraph.mwapi.ApiTemplate.OutputVariable;

import com.bigdata.bop.Constant;
import com.bigdata.bop.IBindingSet;
import com.bigdata.bop.IVariable;
import com.bigdata.bop.IVariableOrConstant;
import com.bigdata.rdf.internal.IV;
import com.bigdata.rdf.internal.VTE;
import com.bigdata.rdf.internal.impl.TermId;
import com.bigdata.rdf.lexicon.LexiconRelation;
import com.bigdata.rdf.sparql.ast.service.BigdataServiceCall;
import com.bigdata.rdf.sparql.ast.service.IServiceOptions;
import com.bigdata.rdf.sparql.ast.service.MockIVReturningServiceCall;

import cutthecrap.utils.striterators.ICloseableIterator;

/**
 * Instance of API service call.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class MWApiServiceCall implements MockIVReturningServiceCall, BigdataServiceCall {
    private static final Logger log = LoggerFactory.getLogger(MWApiServiceCall.class);
    /**
     * Service call template.
     */
    private final ApiTemplate template;
    /**
     * List of input variable bindings.
     */
    private final Map<String, IVariableOrConstant> inputVars;
    /**
     * List of output variable bindings.
     */
    private final List<OutputVariable> outputVars;
    /**
     * HTTP connection.
     */
    private final HttpClient client;
    /**
     * The LexiconRelation for the TripleStore we're working with.
     */
    private final LexiconRelation lexiconRelation;

    MWApiServiceCall(ApiTemplate template,
            Map<String, IVariableOrConstant> inputVars,
            List<OutputVariable> outputVars,
            HttpClient client,
            LexiconRelation lexiconRelation
    ) {
        this.template = template;
        this.inputVars = inputVars;
        this.outputVars = outputVars;
        this.client = client;
        this.lexiconRelation = lexiconRelation;
    }

    @Override
    public IServiceOptions getServiceOptions() {
        return MWApiServiceFactory.SERVICE_OPTIONS;
    }

    @Override
    public ICloseableIterator<IBindingSet> call(IBindingSet[] bindingSets)
            throws Exception {
        return new MultiSearchIterator(bindingSets);
    }

    /**
     * Get HTTP request for this particular query & binding.
     * @param binding
     * @return
     */
    private Request getHttpRequest(IBindingSet binding) {
        // FIXME: real endpoint URL
        Request request = client.newRequest("https://en.wikipedia.org/w/api.php");
        request.method(HttpMethod.GET);
        // Using XML for now to use XPath on responses
        request.param("format", "xml");
        // Add fixed params
        for (Map.Entry<String, String> param : template.getFixedParams().entrySet()) {
            request.param(param.getKey(), param.getValue());
        }
        // Resolve variable params
        for (Map.Entry<String, IVariableOrConstant> term : inputVars.entrySet()) {
            String value = null;
            IV boundValue = null;
            if (term != null) {
                boundValue = (IV)term.getValue().get(binding);
            }
            if (boundValue == null) {
                // try default
                value = template.getInputDefault(term.getKey());
            } else {
                value = boundValue.stringValue();
            }
            if (value == null) {
                throw new IllegalArgumentException("Could not find binding for parameter " + term.getKey());
            }
            request.param(term.getKey(), value);
        }

        return request;
    }

    @Override
    public List<IVariable<IV>> getMockVariables() {
        List<IVariable<IV>> externalVars = new LinkedList<IVariable<IV>>();
        for (OutputVariable v: outputVars) {
            externalVars.add(v.getVar());
        }
        return externalVars;
    }

    /**
     * A chunk of calls to resolve labels.
     */
    private class MultiSearchIterator implements ICloseableIterator<IBindingSet> {
        /**
         * Binding sets being resolved in this chunk.
         */
        private final IBindingSet[] bindingSets;
        /**
         * Has this chunk been closed?
         */
        private boolean closed;
        /**
         * Index of the next binding set to handle when next is next called.
         */
        private int i;
        /**
         * Current search result.
         */
        private SearchResultsIterator searchResult;

        MultiSearchIterator(IBindingSet[] bindingSets) {
            this.bindingSets = bindingSets;
            searchResult = doNextSearch();
        }

        @Override
        public boolean hasNext() {
            if (closed || i >= bindingSets.length) {
                return false;
            }

            if (searchResult == null) {
                return false;
            }

            if (searchResult.hasNext()) {
                return true;
            }

            searchResult = doNextSearch();
            if (searchResult == null) {
                return false;
            } else {
                return searchResult.hasNext();
            }
        }

        /**
         * Produce next search results iterator. Skips over empty results.
         * @return Result iterator or null if no more results.
         */
        private SearchResultsIterator doNextSearch() {
            // Just in case, double check
            if (closed || bindingSets == null || i >= bindingSets.length) {
                searchResult = null;
                return null;
            }
            SearchResultsIterator result;
            do {
                IBindingSet binding = bindingSets[i++];
                result = doSearchFromBinding(binding);
            } while (!result.hasNext() && i < bindingSets.length);
            if (result.hasNext()) {
                return result;
            } else {
                return null;
            }
        }

        /**
         * Execute search for one specific binding set.
         * @param binding
         * @return Search results iterator.
         */
        private SearchResultsIterator doSearchFromBinding(IBindingSet binding) {
            final Request req = getHttpRequest(binding);
            log.info("REQUEST: " + req.getQuery());
            for (OutputVariable var: outputVars) {
                binding.set(var.getVar(), new Constant(mock("TEST")));
            }
            return new SearchResultsIterator(new IBindingSet[] {binding});
        }

        /**
         * Build a mock IV from a literal string.
         */
        private IV mock(String literalString) {
            Literal literal = new LiteralImpl(literalString);
            TermId mock = TermId.mockIV(VTE.LITERAL);
            mock.setValue(lexiconRelation.getValueFactory().asValue(literal));
            return mock;
        }

        @Override
        public IBindingSet next() {
            if (closed || i >= bindingSets.length || searchResult == null) {
                return null;
            }

            if (searchResult.hasNext()) {
                return searchResult.next();
            }

            searchResult = doNextSearch();
            if (searchResult == null || !searchResult.hasNext()) {
                return null;
            } else {
                return searchResult.next();
            }
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    /**
     * Simple iterator for search results.
     */
    private class SearchResultsIterator implements Iterator<IBindingSet> {
        /**
         * The index into the array of hits wrapped by this iterator.
         */
        private int i;
        /**
         * The array of hits wrapped by this iterator.
         */
        private final IBindingSet[] results;

        SearchResultsIterator(final IBindingSet[] results) {
            if (results == null) {
                throw new IllegalArgumentException("Null result?");
            }

            this.results = results;
        }

        @Override
        public boolean hasNext() {
            return i < results.length;
        }

        @Override
        public IBindingSet next() {
            return results[i++];
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String toString() {
            return "SearchResultsIterator{nhits=" + results.length + "} : " + results;
        }
    }
}
