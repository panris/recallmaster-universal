package com.recallmaster.universal.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "recallmaster")
public class RecallMasterProperties {

    @Min(1)
    private int defaultTopK = 5;

    @Valid
    private Evaluator evaluator = new Evaluator();

    @Valid
    private Embedding embedding = new Embedding();

    @Valid
    private List<Database> databases = new ArrayList<>();

    public int getDefaultTopK() {
        return defaultTopK;
    }

    public void setDefaultTopK(int defaultTopK) {
        this.defaultTopK = defaultTopK;
    }

    public Evaluator getEvaluator() {
        return evaluator;
    }

    public void setEvaluator(Evaluator evaluator) {
        this.evaluator = evaluator;
    }

    public Embedding getEmbedding() {
        return embedding;
    }

    public void setEmbedding(Embedding embedding) {
        this.embedding = embedding;
    }

    public List<Database> getDatabases() {
        return databases;
    }

    public void setDatabases(List<Database> databases) {
        this.databases = databases;
    }

    public static class Evaluator {
        @NotBlank
        private String primaryJudge = "rule-based";
        private String secondaryJudge = "";
        private String baseUrl = "";
        private String apiKey = "";
        @Min(1)
        private int concurrency = 5;
        private boolean strictGroundTruth;
        @Min(0)
        private int disagreementThreshold = 20;

        public String getPrimaryJudge() {
            return primaryJudge;
        }

        public void setPrimaryJudge(String primaryJudge) {
            this.primaryJudge = primaryJudge;
        }

        public String getSecondaryJudge() {
            return secondaryJudge;
        }

        public void setSecondaryJudge(String secondaryJudge) {
            this.secondaryJudge = secondaryJudge;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public int getConcurrency() {
            return concurrency;
        }

        public void setConcurrency(int concurrency) {
            this.concurrency = concurrency;
        }

        public boolean isStrictGroundTruth() {
            return strictGroundTruth;
        }

        public void setStrictGroundTruth(boolean strictGroundTruth) {
            this.strictGroundTruth = strictGroundTruth;
        }

        public int getDisagreementThreshold() {
            return disagreementThreshold;
        }

        public void setDisagreementThreshold(int disagreementThreshold) {
            this.disagreementThreshold = disagreementThreshold;
        }
    }

    public static class Embedding {
        private String provider = "hash";
        private String model = "hash-embedding-v1";
        private String baseUrl = "";
        private String apiKey = "";
        @Min(8)
        private int dimensions = 256;

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public int getDimensions() {
            return dimensions;
        }

        public void setDimensions(int dimensions) {
            this.dimensions = dimensions;
        }
    }

    public static class Database {
        @NotBlank
        private String name;
        @NotBlank
        private String type;
        private boolean enabled = true;
        private String connection = "";
        private String uri = "";
        private String apiKey = "";
        private String collection = "";
        private String index = "";
        private String table = "";
        private String idCol = "id";
        private String textCol = "text";
        private String vectorCol = "embedding";
        private String metadataCol = "metadata";
        private String metric = "cosine";
        @Min(8)
        private int dimension = 256;
        private Map<String, String> params = new LinkedHashMap<>();
        private List<SeedDocument> documents = new ArrayList<>();

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getConnection() {
            return connection;
        }

        public void setConnection(String connection) {
            this.connection = connection;
        }

        public String getUri() {
            return uri;
        }

        public void setUri(String uri) {
            this.uri = uri;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getCollection() {
            return collection;
        }

        public void setCollection(String collection) {
            this.collection = collection;
        }

        public String getIndex() {
            return index;
        }

        public void setIndex(String index) {
            this.index = index;
        }

        public String getTable() {
            return table;
        }

        public void setTable(String table) {
            this.table = table;
        }

        public String getIdCol() {
            return idCol;
        }

        public void setIdCol(String idCol) {
            this.idCol = idCol;
        }

        public String getTextCol() {
            return textCol;
        }

        public void setTextCol(String textCol) {
            this.textCol = textCol;
        }

        public String getVectorCol() {
            return vectorCol;
        }

        public void setVectorCol(String vectorCol) {
            this.vectorCol = vectorCol;
        }

        public String getMetadataCol() {
            return metadataCol;
        }

        public void setMetadataCol(String metadataCol) {
            this.metadataCol = metadataCol;
        }

        public String getMetric() {
            return metric;
        }

        public void setMetric(String metric) {
            this.metric = metric;
        }

        public int getDimension() {
            return dimension;
        }

        public void setDimension(int dimension) {
            this.dimension = dimension;
        }

        public Map<String, String> getParams() {
            return params;
        }

        public void setParams(Map<String, String> params) {
            this.params = params;
        }

        public List<SeedDocument> getDocuments() {
            return documents;
        }

        public void setDocuments(List<SeedDocument> documents) {
            this.documents = documents;
        }
    }

    public static class SeedDocument {
        @NotBlank
        private String id;
        @NotBlank
        private String text;
        private Map<String, String> metadata = new LinkedHashMap<>();

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public Map<String, String> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, String> metadata) {
            this.metadata = metadata;
        }
    }
}
