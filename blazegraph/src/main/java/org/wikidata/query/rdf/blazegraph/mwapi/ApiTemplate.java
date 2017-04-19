package org.wikidata.query.rdf.blazegraph.mwapi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.bigdata.bop.IVariable;
import com.bigdata.bop.IVariableOrConstant;
import com.bigdata.rdf.sparql.ast.TermNode;
import com.bigdata.rdf.sparql.ast.eval.ServiceParams;
import com.fasterxml.jackson.databind.JsonNode;

import static org.wikidata.query.rdf.blazegraph.mwapi.MWApiServiceFactory.paramNameToURI;
/**
 * This class represents API template.
 */
public final class ApiTemplate {
    /**
     * Set of fixed API parameters.
     */
    private final Map<String, String> fixedParams = new HashMap<>();
    /**
     * Set of API parameters that should come from input vars.
     * The value is the default.
     */
    private final Map<String, String> inputVars = new HashMap<>();
    /**
     * Set of API parameters that should be sent to output.
     * The value is the XPath to find the value.
     */
    private final Map<String, String> outputVars = new HashMap<>();
    /**
     * XPath to result items.
     */
    private String items;
    /**
     * Hidden ctor.
     * Use fromJSON() to create the object.
     */
    private ApiTemplate() {}

    /**
     * Create API template from JSON configuration.
     * @param json
     * @return
     */
    public static ApiTemplate fromJSON(JsonNode json) {
        ApiTemplate template = new ApiTemplate();

        // Parse input params
        final JsonNode params = json.get("params");
        for (String paramName: (Iterable<String>)() -> params.fieldNames()) {
            if (template.fixedParams.containsKey(paramName)
                    || template.inputVars.containsKey(paramName)) {
                throw new IllegalArgumentException(
                        "Repeated input parameter " + paramName);
            }

            JsonNode value = params.get(paramName);
            // scalar value means fixed parameter
            if (value.isValueNode()) {
                template.fixedParams.put(paramName, value.asText());
            }
            // otherwise it's a parameter
            // TODO: ignoring type for now
            if (value.has("default")) {
                template.inputVars.put(paramName, value.get("default").asText());
            } else {
                template.inputVars.put(paramName, null);
            }
        }

        // Parse output params
        final JsonNode output = json.get("output");
        template.items = output.get("items").asText();
        final JsonNode vars = output.get("vars");
        for (String paramName: (Iterable<String>)() -> vars.fieldNames()) {
            if (template.inputVars.containsKey(paramName)) {
                throw new IllegalArgumentException("Parameter " + paramName + " declared as both input and output");
            }
            template.outputVars.put(paramName, vars.get(paramName).asText());
        }

        return template;
    }

    /**
     * Get items XPath.
     * @return
     */
    public String getItemsPath() {
        return items;
    }

    /**
     * Get call fixed parameters.
     * @return
     */
    public Map<String, String> getFixedParams() {
        return fixedParams;
    }

    /**
     * Find default for this parameter.
     * @param name
     * @return Default value or null.
     */
    public String getInputDefault(String name) {
        return inputVars.get(name);
    }

    /**
     * Create list of bindings from input params to specific variables or constants.
     * @param serviceParams Specific invocation params.
     * @return
     */
    public Map<String, IVariableOrConstant> getInputVars(final ServiceParams serviceParams) {
        Map<String, IVariableOrConstant> vars = new HashMap<>(inputVars.size());

        for (Map.Entry<String, String> entry : inputVars.entrySet()) {
            TermNode var = serviceParams.get(paramNameToURI(entry.getKey()), null);
            if (var == null) {
                if (entry.getValue() == null) {
                    // Param should have either binding or default
                    throw new IllegalArgumentException("Parameter " + entry.getKey() + " must be bound");
                }
                // If var is null but we have a default, put null there, service call will know
                // how to handle it.
                vars.put(entry.getKey(), null);
            } else {
                if (!var.isConstant() && !var.isVariable()) {
                    // Binding should be constant or var
                    throw new IllegalArgumentException("Parameter " + entry.getKey() + " must be constant or variable");
                }
                vars.put(entry.getKey(), var.getValueExpression());
            }
        }
        return vars;
    }

    /**
     * Create map of output variables from template and service params.
     * @param serviceParams Specific invocation params.
     * @return
     */
    public List<OutputVariable> getOutputVars(final ServiceParams serviceParams) {
        List<OutputVariable> vars = new ArrayList<>(outputVars.size());
        for (Map.Entry<String, String> entry : outputVars.entrySet()) {
            TermNode varNode = serviceParams.get(paramNameToURI(entry.getKey()), null);
            if (varNode == null) {
                // It's always OK to ignore output vars
                continue;
            }
            if (!varNode.isVariable()) {
                throw new IllegalArgumentException("Output parameter " + entry.getKey() + " must be bound to a variable");
            }
            IVariable var = (IVariable)varNode.getValueExpression();
            if (var.isAnonymous()) {
                // Using anonymous is useless, but we'll let it pass.
                continue;
            }
            vars.add(new OutputVariable(var, entry.getValue()));
        }
        return vars;
    }

    /**
     * Variable in the output of the API.
     */
    public static class OutputVariable {
        /**
         * Original Blazegraph var.
         */
        private final IVariable var;
        /**
         * XPath expression to extract value from result.
         */
        private final String xpath;

        public OutputVariable(IVariable var, String xpath) {
            this.var = var;
            this.xpath = xpath;
        }

        /**
         * Get associated variable.
         * @return
         */
        public IVariable getVar() {
            return var;
        }

        /**
         * Get associated variable name.
         * @return
         */
        public String getName() {
            return var.getName();
        }
    }

}
