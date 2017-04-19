package org.wikidata.query.rdf.blazegraph.mwapi;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.openrdf.model.URI;
import org.openrdf.model.impl.URIImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wikidata.query.rdf.common.uri.Ontology;

import com.bigdata.rdf.sparql.ast.eval.AbstractServiceFactory;
import com.bigdata.rdf.sparql.ast.eval.ServiceParams;
import com.bigdata.rdf.sparql.ast.service.BigdataNativeServiceOptions;
import com.bigdata.rdf.sparql.ast.service.BigdataServiceCall;
import com.bigdata.rdf.sparql.ast.service.IServiceOptions;
import com.bigdata.rdf.sparql.ast.service.ServiceCallCreateParams;
import com.bigdata.rdf.sparql.ast.service.ServiceNode;
import com.bigdata.rdf.sparql.ast.service.ServiceRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Service factory for calling out to Mediawiki API Services.
 * Service call looks like:
 *
 *  SERVICE wikibase:mwapi {
 *      bd:serviceParam wikibase:api "Categories" .
 *      bd:serviceParam :titles "Albert Einstein" .
 *      bd:serviceParam :category ?category .
 *      bd:serviceParam :title ?title .
 *  }
 */
public class MWApiServiceFactory extends AbstractServiceFactory {
    private static final Logger log = LoggerFactory.getLogger(MWApiServiceFactory.class);

    /**
     * Options configuring this service as a native Blazegraph service.
     */
    public static final BigdataNativeServiceOptions SERVICE_OPTIONS = new BigdataNativeServiceOptions();
    /**
     * Service config.
     */
    private final Map<String, ApiTemplate> config = new HashMap<>();
    /**
     * The URI service key.
     */
    public static final URI SERVICE_KEY = new URIImpl(Ontology.NAMESPACE + "mwapi");
    /**
     * API type parameter name.
     */
    public static final URI API_KEY = new URIImpl(Ontology.NAMESPACE + "api");
    /**
     * Default service config filename.
     */
    public static final String CONFIG_DEFAULT = "mwservices.json";
    /**
     * Config file parameter.
     */
    public static final String CONFIG_NAME = MWApiServiceFactory.class.getName() + ".config";
    /**
     * Filename of the config.
     */
    public static final String CONFIG_FILE = System.getProperty(CONFIG_NAME, CONFIG_DEFAULT);

    public MWApiServiceFactory() throws IOException {
        log.info("Loading MWAPI service configuration from " + CONFIG_FILE);
        loadJSONConfig(new InputStreamReader(new FileInputStream(CONFIG_FILE), StandardCharsets.UTF_8));
        log.info("Registered " + config.size() + " services.");
    }

    /**
     * @throws IOException
     * Load config from JSON object.
     * @param configReader
     */
    public void loadJSONConfig(Reader configReader) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree(IOUtils.toString(configReader));
        for (String serviceName: (Iterable<String>)() -> node.fieldNames()) {
            config.put(serviceName, ApiTemplate.fromJSON(node.get(serviceName)));
        }
    }

    @Override
    public IServiceOptions getServiceOptions() {
        return SERVICE_OPTIONS;
    }

    /**
     * Register the service so it is recognized by Blazegraph.
     */
    public static void register() {
        final ServiceRegistry reg = ServiceRegistry.getInstance();
        try {
            reg.add(SERVICE_KEY, new MWApiServiceFactory());
        } catch (IOException e) {
            // Do not add to whitelist if init failed.
            log.warn("MW Service registration failed: " + e);
            return;
        }
        reg.addWhitelistURL(SERVICE_KEY.toString());
    }

    @Override
    public BigdataServiceCall create(ServiceCallCreateParams params, final ServiceParams serviceParams) {
        final ServiceNode serviceNode = params.getServiceNode();

        if (serviceNode == null) {
            throw new IllegalArgumentException();
        }

        final ApiTemplate template = getServiceTemplate(serviceParams);

        return new MWApiServiceCall(template,
                template.getInputVars(serviceParams),
                template.getOutputVars(serviceParams),
                params.getClientConnectionManager(),
                params.getTripleStore().getLexiconRelation()
                );
    }

    /**
     * Extract service template name from params.
     * @param serviceParams
     * @return Service template
     */
    private ApiTemplate getServiceTemplate(final ServiceParams serviceParams) {
        final String templateName = serviceParams.getAsString(API_KEY);
        serviceParams.clear(API_KEY);
        if (!config.containsKey(templateName)) {
            throw new IllegalArgumentException(
                    "Service name " + templateName + " not found in configuration");
        }
        return config.get(templateName);
    }

    /**
     * Create predicate parameter URI from name.
     * TODO: for now, identical to name, check if that works.
     * @param name
     * @return
     */
    public static URI paramNameToURI(String name) {
        return new URIImpl(name);
    }
}
