package ai.router.utils;

public class RouteGlobal {
    public static final char GROUP_START = '(';
    public static final char GROUP_END = ')';

    public static final char POLLING_SEPARATOR = '|';
    public static final char PARALLEL_SEPARATOR = '&';
    public static final char FAILOVER_SEPARATOR = ',';

    public static final String WILDCARD_STRING = "%";

    public static String MAPPER_CHAT_REQUEST = "MAPPER_CHAT_REQUEST";
    public static String MAPPER_AGENT_LIST = "MAPPER_AGENT_LIST";
    public static String MAPPER_RAG_URL = "MAPPER_RAG_URL";
    public static String MAPPER_AGENT_CONFIG = "MAPPER_AGENT_CONFIG";

    public static final String POLLING = "polling";
    public static final String FAILOVER = "failover";
}
