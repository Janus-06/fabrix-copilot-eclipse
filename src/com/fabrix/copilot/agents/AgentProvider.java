package com.fabrix.copilot.agents;

/**
 * 🎯 AgentProvider (신규)
 * * 플러그인 전체에서 사용될 에이전트들의 싱글톤 인스턴스를 제공합니다.
 * 이 클래스를 통해 불필요한 객체 생성을 막고 메모리 사용을 최적화합니다.
 */
public final class AgentProvider {

    private static final GeneralAgent GENERAL_AGENT = new GeneralAgent();
    private static final CodingAgent CODING_AGENT = new CodingAgent();
    private static final McpAgent MCP_AGENT = new McpAgent();
    private static final SelfCritiqueAgent SELF_CRITIQUE_AGENT = new SelfCritiqueAgent();
    
    // ReactAgent는 다른 에이전트들을 사용하므로 별도 초기화
    private static final ReactAgent REACT_AGENT = new ReactAgent();

    // private 생성자로 외부에서 인스턴스화 방지
    private AgentProvider() {}

    public static GeneralAgent getGeneralAgent() {
        return GENERAL_AGENT;
    }

    public static CodingAgent getCodingAgent() {
        return CODING_AGENT;
    }

    public static McpAgent getMcpAgent() {
        return MCP_AGENT;
    }

    public static SelfCritiqueAgent getSelfCritiqueAgent() {
        return SELF_CRITIQUE_AGENT;
    }
    
    public static ReactAgent getReactAgent() {
        return REACT_AGENT;
    }
}