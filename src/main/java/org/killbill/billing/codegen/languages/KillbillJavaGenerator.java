package org.killbill.billing.codegen.languages;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import io.swagger.codegen.*;
import io.swagger.codegen.languages.AbstractJavaCodegen;
import io.swagger.models.Swagger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;

public class KillbillJavaGenerator extends AbstractJavaCodegen implements CodegenConfig {


    private static final String NAME = "killbill-java";


    private static final String GROUP_ID = "org.kill-bill.billing";
    private static final String ARTIFACT_ID = "killbill-client-java-gen";
    private static final String VERSION = "0.0.1-SNAPSHOT";

    private static final String DOCS_PATH = "docs/";

    private static final String API_PACKAGE = "org.killbill.billing.client.api.gen";
    private static final String MODEL_PACKAGE = "org.killbill.billing.client.model.gen";

    private static final String HTTP_USER_AGENT = "killbill/java-client";

    private static final String DATE_LIBRARY_JODA = "joda";

    // Keep track of all visited Reference Models
    private static Set<String> ALL_MODELS = new HashSet<>();
    
    private static String QUERY_REQUESTED_DT = "requestedDate";
    private static String QUERY_ENTITLEMENT_REQUESTED_DT = "entitlementDate";
    private static String QUERY_BILLING_REQUESTED_DT = "billingDate";
    
    private static String CREATE_SUBSCRIPTION = "createSubscription";
    private static String ADD_BUNDLE_BLOCKING_STATE = "addBundleBlockingState";
    private static String CREATE_SUBSCRIPTION_WITH_ADDONS = "createSubscriptionWithAddOns";
    private static String CANCEL_SUBSCRIPTION = "cancelSubscriptionPlan";
    private static String CHANGE_SUBSCRIPTION_PLAN = "changeSubscriptionPlan";
    private static String ADD_SUBSCRIPTION_BLOCKING_STATE = "addSubscriptionBlockingState";
    

    /**
     * Configures the type of generator.
     *
     * @return the CodegenType for this generator
     * @see io.swagger.codegen.CodegenType
     */
    public CodegenType getTag() {
        return CodegenType.CLIENT;
    }

    /**
     * Configures a friendly name for the generator.  This will be used by the generator
     * to select the library with the -l flag.
     *
     * @return the friendly name for the generator
     */
    public String getName() {
        return NAME;
    }

    /**
     * Returns human-friendly help for the generator.  Provide the consumer with help
     * tips, parameters here
     *
     * @return A string value for the help message
     */
    public String getHelp() {
        return "Generates a killbill-java client library.";
    }

    private final Map<String, Class> apiEnums;

    public KillbillJavaGenerator() {
        super();
        templateDir = "killbill-java";
        embeddedTemplateDir = "killbill-java";

        final String kbApiJar = System.getProperty("kbApiJar");
        if (kbApiJar == null) {
            throw new IllegalArgumentException("Need to specify KB api version: -DkbApiJar=<location of the jar>");
        }

        try {
            apiEnums = ClassUtil.findAPIEnum(kbApiJar);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void processOpts() {
        additionalProperties.put(DATE_LIBRARY, DATE_LIBRARY_JODA);

        super.processOpts();

        groupId = GROUP_ID;
        artifactId = ARTIFACT_ID;
        artifactVersion = VERSION;

        apiPackage = API_PACKAGE;
        modelPackage = MODEL_PACKAGE;

        apiDocPath = DOCS_PATH;

        httpUserAgent = HTTP_USER_AGENT;

        hideGenerationTimestamp = true; /* Does not seem to work, use cli option to disable */


        typeMapping.put("array", "List");
        typeMapping.put("map", "Map");
        typeMapping.put("date", "LocalDate");
        typeMapping.put("DateTime", "ZonedDateTime");

        typeMapping.put("file", "java.io.File");



        importMapping.clear();
        importMapping.put("UUID", "java.util.UUID");
        importMapping.put("Collection", "java.util.Collection");
        importMapping.put("List", "java.util.List");
        importMapping.put("LinkedList", "java.util.LinkedList");
        importMapping.put("ArrayList", "java.util.ArrayList");
        importMapping.put("ZonedDateTime", "java.time.ZonedDateTime");
        importMapping.put("LocalDate", "java.time.LocalDate");
        importMapping.put("BigDecimal", "java.math.BigDecimal");
        importMapping.put("HashMap", "java.util.HashMap");
        importMapping.put("Map", "java.util.Map");
        importMapping.put("JsonProperty", "com.fasterxml.jackson.annotation.JsonProperty");

        // So as to not generate Entity
        importMapping.put("Entity", "org.killbill.billing.client.model.gen");

        // Kill Bill API
        for (final String key : apiEnums.keySet()) {
            importMapping.put(key, apiEnums.get(key).getName().replaceAll("\\$", "."));
        }

        instantiationTypes.put("List", "java.util.ArrayList");
        instantiationTypes.put("Map", "java.util.HashMap");
    }

    @Override
    public void preprocessSwagger(Swagger swagger) {
    }


    private  String findEnumType(final String firstEnumValue) {
        for (final String key : apiEnums.keySet()) {
            Class claz = apiEnums.get(key);
            if (EnumSet.allOf(claz).iterator().next().toString().equals(firstEnumValue)) {
                return key;
            }

        }
        throw new IllegalStateException("Cannot find enum with first value " + firstEnumValue);
    }


    @Override
    public Map<String, Object> postProcessModels(Map<String, Object> objs) {
        final List<Object> models = (List<Object>) objs.get("models");
        for (Object entries :  models) {
            Map<String, Object> modelTemplate = (Map<String, Object>) entries;
            final CodegenModel m = (CodegenModel) modelTemplate.get("model");
            if (m.name.equals("Entity")) {
                return Collections.emptyMap();
            }

            final Iterator<CodegenProperty> it  = m.vars.iterator();
            if (m.name.equals("AuditLog")) {
                m.vendorExtensions.put("x-entity-generic", true);
                while (it.hasNext()) {
                    final CodegenProperty p = it.next();
                    if (p.name.equals("history")) {
                        p.vendorExtensions.put("x-entity-generic", true);
                        m.imports.remove("Entity");
                        break;
                    }
                }
            } else {
                while (it.hasNext()) {
                    final CodegenProperty p = it.next();
                    if (p.name.equals("auditLogs")) {
                        p.isInherited = true;
                        m.parent = "KillBillObject";
                        p.vendorExtensions.put("x-ignore-for-identity", true);
                        m.vendorExtensions.put("x-ignore-super-for-identity", true);
                        break;
                    }
                }
            }

           ALL_MODELS.add(m.name);
        }

        return objs;
    }

    // Transform CodegenOperation obj into ExtendedCodegenOperation to add properties into mustache maps
    @Override
    public Map<String, Object> postProcessOperations(Map<String, Object> objs) {
        final Map<String, Object> operationsMap = (Map<String, Object>) super.postProcessOperations(objs).get("operations");
        final List<CodegenOperation> operations = (List<CodegenOperation>) operationsMap.get("operation");
        final List<ExtendedCodegenOperation> extOperations = new ArrayList<>(operations.size());
        final List<Map<String, String>> imports = (List<Map<String, String>>) objs.get("imports");
        for (CodegenOperation op : operations) {
            final ExtendedCodegenOperation ext = new ExtendedCodegenOperation(op);
            extOperations.add(ext);
            addAllImportsIfRequired(ext, imports);
            convertToExtendedCodegenParam(ext, imports);
            if (shouldAddDateTimeMethod(op)) {
                addImportIfRequired(imports, "java.time.ZonedDateTime");
                addImportIfRequired(imports, "java.time.LocalDate");
                final ExtendedCodegenOperation ext2 = new ExtendedCodegenOperation(op);
                addAllImportsIfRequired(ext2, imports);
                for (List<CodegenParameter> paramList : Arrays.asList(ext2.allParams, ext2.queryParams)) {
                    for (CodegenParameter parameter : paramList) {
                        if (isDateParameter(parameter)) {
                            parameter.dataFormat = "date-time";
                            parameter.isDate = false;
                            parameter.isDateTime = true;
                            parameter.dataType = "ZonedDateTime";
                        }
                    }
                }
                convertToExtendedCodegenParam(ext2, imports);
                extOperations.add(ext2);
            }
        }
        operationsMap.put("operation", extOperations);
        return objs;
    }
     
    private void addAllImportsIfRequired(ExtendedCodegenOperation ext, List<Map<String, String>> imports) {
        if (ext.isReturnModelRefContainer) {
            addImportIfRequired(imports, String.format("org.killbill.billing.client.model.%s", ext.returnType));
        }
        if (ext.isBodyModelRefContainer) {
            addImportIfRequired(imports, String.format("org.killbill.billing.client.model.%s", ext.bodyParam.dataType));
        }
        if (ext.isStream) {
            addImportIfRequired(imports, "java.io.InputStream");
            addImportIfRequired(imports, "java.io.OutputStream");
            addImportIfRequired(imports, "java.net.http.HttpResponse");
        }
        if (ext.isListContainer) {
            addImportIfRequired(imports, "java.util.List");
        } else if (ext.isMapContainer) {
            addImportIfRequired(imports, "java.util.Map");
        }

    }
    
    private void convertToExtendedCodegenParam(ExtendedCodegenOperation ext, List<Map<String, String>> imports) {
        // Unfortunately those lists contain different objects -- so a query param from all param is really a different
        // java object and if needed needs to be converted for each list

        ext.allParams = convertToExtendedCodegenParam(ext.allParams, imports);
        ext.bodyParams = convertToExtendedCodegenParam(ext.bodyParams, imports);
        ext.pathParams = convertToExtendedCodegenParam(ext.pathParams, imports);
        ext.queryParams = convertToExtendedCodegenParam(ext.queryParams, imports);
        ext.formParams = convertToExtendedCodegenParam(ext.formParams, imports);
    }


    private boolean shouldAddDateTimeMethod(CodegenOperation op) {
        return op.operationId.equalsIgnoreCase(ADD_BUNDLE_BLOCKING_STATE) || op.operationId.equalsIgnoreCase(ADD_SUBSCRIPTION_BLOCKING_STATE) || op.operationId.equalsIgnoreCase(CREATE_SUBSCRIPTION) || op.operationId.equalsIgnoreCase(CREATE_SUBSCRIPTION_WITH_ADDONS) || op.operationId.equalsIgnoreCase(CANCEL_SUBSCRIPTION) || op.operationId.equalsIgnoreCase(CHANGE_SUBSCRIPTION_PLAN);
    }
    private boolean isDateParameter(CodegenParameter parameter) {
        return parameter.baseName.equalsIgnoreCase(QUERY_BILLING_REQUESTED_DT) || parameter.baseName.equalsIgnoreCase(QUERY_ENTITLEMENT_REQUESTED_DT) || parameter.baseName.equalsIgnoreCase(QUERY_REQUESTED_DT);
    }


    private List<CodegenParameter> convertToExtendedCodegenParam(final List<CodegenParameter> input, @Nullable final List<Map<String, String>> imports) {
        final List<CodegenParameter> result = new ArrayList<>(input.size());
        for (final CodegenParameter p : input) {
            if (p.isEnum) {
                if (!p.isContainer) {
                    final String enumImport = importMapping.get(p.datatypeWithEnum);
                    addImportIfRequired(imports, enumImport);
                } else if (p.items.isEnum) {
                    final String enumImport = importMapping.get(p.items.datatypeWithEnum);
                    addImportIfRequired(imports, enumImport);
                }
            }
            final ExtendedCodegenParameter e = new ExtendedCodegenParameter(p);
            if (e.isListContainer) {
                addImportIfRequired(imports, "java.util.List");
            } else if (e.isMapContainer) {
                addImportIfRequired(imports, "java.util.Map");
            }
            result.add(e);
        }
        return result;
    }

    private void addImportIfRequired(@Nullable final List<Map<String, String>> imports, String newImport) {
        if (imports == null) {
            return;
        }
        for (Map<String, String> im : imports) {
            for (String i : im.values()) {
                if (i.equals(newImport)) {
                    return;
                }
            }
        }
        final Map<String, String> im = new LinkedHashMap<String, String>();
        im.put("import", newImport);
        imports.add(im);
    }

    @Override
    public String toEnumName(CodegenProperty property) {
        if (property.isEnum) {
            if (property._enum != null && property._enum.size() > 0) {
                final String guessedEnumType = findEnumType(property._enum.get(0));
                return guessedEnumType;
            } else if (property.datatypeWithEnum != null) {
                return property.datatypeWithEnum;
            }
        }
        System.err.println("Mising _enum for " + property.name);
        return sanitizeName(camelize(property.name));
    }

    @Override
    public void postProcessModelProperty(CodegenModel model, CodegenProperty property) {
        if (property.isEnum) {
            if (!property.isContainer) {
                final String guessedEnumType = findEnumType(property._enum.get(0));
                model.imports.add(guessedEnumType);
            } else if (property.items.isEnum) {
                final String guessedEnumType = findEnumType(property.items._enum.get(0));
                model.imports.add(guessedEnumType);
            }
        }
        if (property.isBoolean) {
            model.imports.add("JsonProperty");
        }
    }

    @Override
    public String toBooleanGetter(String name) {
        // boolean properties from swagger schema are 'isXXX' and we want to leave the getter method as such.
        return toVarName(name);
    }


    private static class ExtendedCodegenOperation extends CodegenOperation {

        public boolean isGet,
                isPost,
                isDelete,
                isPut,
                isOptions,
                isReturnModelRefContainer,
                isBodyModelRefContainer,
                hasNonRequiredDefaultQueryParams,
                isStream;


        private ExtendedCodegenOperation(CodegenOperation o) {
            super();

            this.path = o.path;
            this.responseHeaders.addAll(o.responseHeaders);
            this.hasAuthMethods = o.hasAuthMethods;
            this.hasConsumes = o.hasConsumes;
            this.hasProduces = o.hasProduces;
            this.hasParams = o.hasParams;
            this.hasOptionalParams = o.hasOptionalParams;
            this.returnTypeIsPrimitive = o.returnTypeIsPrimitive;
            this.returnSimpleType = o.returnSimpleType;
            this.subresourceOperation = o.subresourceOperation;
            this.isMapContainer = o.isMapContainer;
            this.isListContainer = o.isListContainer;
            this.isMultipart = o.isMultipart;
            this.hasMore = o.hasMore;
            this.isResponseBinary = o.isResponseBinary;
            this.hasReference = o.hasReference;
            this.isRestfulIndex = o.isRestfulIndex;
            this.isRestfulShow = o.isRestfulShow;
            this.isRestfulCreate = o.isRestfulCreate;
            this.isRestfulUpdate = o.isRestfulUpdate;
            this.isRestfulDestroy = o.isRestfulDestroy;
            this.isRestful = o.isRestful;
            this.operationId = o.operationId;
            this.returnType = o.returnType;
            this.httpMethod = o.httpMethod;
            this.returnBaseType = o.returnBaseType;
            this.returnContainer = o.returnContainer;
            this.summary = o.summary;
            this.unescapedNotes = o.unescapedNotes;
            this.notes = o.notes;
            this.baseName = o.baseName;
            this.defaultResponse = o.defaultResponse;
            this.discriminator = o.discriminator;
            this.consumes = o.consumes;
            this.produces = o.produces;
            this.bodyParam = o.bodyParam;
            this.allParams = o.allParams;
            this.bodyParams = o.bodyParams;
            this.pathParams = o.pathParams;
            this.queryParams = o.queryParams;
            this.headerParams = o.headerParams;
            this.formParams = o.formParams;
            this.authMethods = o.authMethods;
            this.tags = o.tags;
            this.responses = o.responses;
            this.imports = o.imports;
            this.examples = o.examples;
            this.externalDocs = o.externalDocs;
            this.vendorExtensions = o.vendorExtensions;
            this.nickname = o.nickname;
            this.operationIdLowerCase = o.operationIdLowerCase;
            this.isGet = "GET".equalsIgnoreCase(httpMethod);
            this.isPost = "POST".equalsIgnoreCase(httpMethod);
            this.isPut = "PUT".equalsIgnoreCase(httpMethod);
            this.isDelete = "DELETE".equalsIgnoreCase(httpMethod);
            this.isOptions = "OPTIONS".equalsIgnoreCase(httpMethod);
            // Body is a List of Reference Model
            this.isBodyModelRefContainer = (isPost || isPut) &&
                    bodyParam != null && bodyParam.isContainer && bodyParam.baseType != null &&
                    ALL_MODELS.contains(bodyParam.baseType);
            if (isBodyModelRefContainer) {
                this.bodyParam.dataType = String.format("%ss", this.bodyParam.baseType);
            }

            // Return value is a List of Reference Model
            // Actually here, we need to create special types for primitive types as well:
            // e.g we want to generate: return doGet(uri, DateTimes.class, requestOptions);
            //     and not: return doGet(uri, List<DateTime>.class, requestOptions);
            // => So we don't restrict with ALL_MODELS.contains(this.returnBaseType);
            this.isReturnModelRefContainer = returnContainer != null &&
                    //ALL_MODELS.contains(this.returnBaseType) &&
                    returnContainer.equals("array") && this.returnBaseType != null;

            if (isReturnModelRefContainer) {
                // ZonedDateTime wrapper class is still called DateTimes (hand-maintained)
                if ("ZonedDateTime".equals(this.returnBaseType)) {
                    this.returnType = "DateTimes";
                } else {
                    this.returnType = String.format("%ss", this.returnBaseType);
                }
            }
            this.isStream = produces != null && !produces.isEmpty() && produces.get(0).get("mediaType").equals("application/octet-stream");
            this.hasNonRequiredDefaultQueryParams = this.queryParams
                    .stream()
                    .anyMatch(input -> !input.required && input.defaultValue != null);
            this.hasNonRequiredDefaultQueryParams = Iterables.any(this.queryParams, new Predicate<CodegenParameter>() {
                @Override
                public boolean apply(CodegenParameter input) {
                    return !input.required && input.defaultValue != null;
                }
            });
        }
    }
    
    private static class ExtendedCodegenParameter extends CodegenParameter {

        public boolean isMandatoryParam;
        public String formattedDefault;
        public boolean isQueryPluginProperty;

        public ExtendedCodegenParameter(CodegenParameter p) {
            //
            // We treat pluginProperty query params differently:
            // Instead of generating ' List<String> pluginProperty', we generate Map<String, String> pluginProperty
            // and some glue code to correctly serialize those as query param.
            //
            this.isQueryPluginProperty = p.baseName != null && p.baseName.equals("pluginProperty") && p.isQueryParam && p.isListContainer;
            this.isFile = p.isFile;
            this.notFile = p.notFile;
            this.hasMore = p.hasMore;
            this.isContainer = p.isContainer;
            this.secondaryParam = p.secondaryParam;
            this.baseName = p.baseName;
            this.paramName = p.paramName;
            this.dataType = isQueryPluginProperty ? "Map<String, String>" : p.dataType;
            this.datatypeWithEnum = p.datatypeWithEnum;
            this.enumName = p.enumName;
            this.dataFormat = p.dataFormat;
            this.collectionFormat = p.collectionFormat;
            this.isCollectionFormatMulti = p.isCollectionFormatMulti;
            this.isPrimitiveType = p.isPrimitiveType;
            this.description = p.description;
            this.unescapedDescription = p.unescapedDescription;
            this.baseType = p.baseType;
            this.isFormParam = p.isFormParam;
            this.isQueryParam = p.isQueryParam;
            this.isPathParam = p.isPathParam;
            this.isHeaderParam = p.isHeaderParam;
            this.isCookieParam = p.isCookieParam;
            this.isBodyParam = p.isBodyParam;
            this.required = p.required;
            this.maximum = p.maximum;
            this.exclusiveMaximum = p.exclusiveMaximum;
            this.minimum = p.minimum;
            this.exclusiveMinimum = p.exclusiveMinimum;
            this.maxLength = p.maxLength;
            this.minLength = p.minLength;
            this.pattern = p.pattern;
            this.maxItems = p.maxItems;
            this.minItems = p.minItems;
            this.uniqueItems = p.uniqueItems;
            this.multipleOf = p.multipleOf;
            this.jsonSchema = p.jsonSchema;
            this.defaultValue = p.defaultValue;
            this.example = p.example;
            this.isEnum = p.isEnum;
            this._enum = p._enum;
            this.allowableValues = p.allowableValues;
            this.items = p.items;
            this.vendorExtensions = p.vendorExtensions;
            this.hasValidation = p.hasValidation;
            this.isBinary = p.isBinary;
            this.isByteArray = p.isByteArray;
            this.isString = p.isString;
            this.isNumeric = p.isNumeric;
            this.isInteger = p.isInteger;
            this.isLong = p.isLong;
            this.isDouble = p.isDouble;
            this.isFloat = p.isFloat;
            this.isNumber = p.isNumber;
            this.isBoolean = p.isBoolean;
            this.isDate = p.isDate || (dataFormat != null && dataFormat.equalsIgnoreCase("date"));
            this.isDateTime = p.isDateTime || (dataFormat != null && dataFormat.equalsIgnoreCase("date-time"));
            this.isUuid = p.isUuid || (dataFormat != null && dataFormat.equalsIgnoreCase("uuid"));
            this.isListContainer = p.isListContainer && !isQueryPluginProperty;
            this.isMapContainer = p.isMapContainer || isQueryPluginProperty;
            this.isMandatoryParam = !isHeaderParam && (required || (isQueryParam && defaultValue == null));
            if (!isMandatoryParam && defaultValue != null) {
                if (isLong) {
                    this.formattedDefault = String.format("Long.valueOf(%s)", defaultValue);
                } else if (isInteger) {
                    this.formattedDefault = String.format("Integer.valueOf(%s)", defaultValue);
                } else if (isDouble) {
                    this.formattedDefault = String.format("Double.valueOf(%s)", defaultValue);
                } else if (isFloat) {
                    this.formattedDefault = String.format("Float.valueOf(%s)", defaultValue);
                } else if (isEnum) {
                    this.formattedDefault = String.format("%s.%s", enumName, defaultValue);
                } else if (isBoolean) {
                    this.formattedDefault = String.format("Boolean.valueOf(%s)", defaultValue);
                } else {
                    throw new IllegalStateException(String.format("FIXME: Need to implement formatted default value for type %s", baseType));
                }
            }
        }
    }

}