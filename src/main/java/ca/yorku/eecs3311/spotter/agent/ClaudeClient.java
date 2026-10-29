package ca.yorku.eecs3311.spotter.agent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.agent.tool
        .JsonSchemaProperty;
import dev.langchain4j.agent.tool
        .ToolExecutionRequest;
import dev.langchain4j.agent.tool
        .ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.anthropic
        .AnthropicChatModel;
import dev.langchain4j.model.chat
        .ChatLanguageModel;
import dev.langchain4j.model.output.Response;

import ca.yorku.eecs3311.spotter
        .infrastructure.config.ConfigurationException;
import ca.yorku.eecs3311.spotter
        .infrastructure.config.SpotterConfig;

/**
 * Talks to Anthropic Claude through LangChain4j.
 *
 * This is the only class in Spotter that imports
 * LangChain4j or knows a vendor's name. Because
 * everything above depends on LLMClient instead,
 * the provider can be changed here and nothing
 * else moves.
 *
 * Two models are kept, a fast one and a stronger
 * one, so a task that only needs a sentence of
 * rationale is not charged at the rate of one
 * that plans a whole block.
 *
 * Both are built on first use rather than in the
 * constructor, so the deterministic half of
 * Spotter runs with no API key present at all.
 */
public final class ClaudeClient
        implements LLMClient {

    private final SpotterConfig config;
    private final ObjectMapper json =
            new ObjectMapper();

    private ChatLanguageModel fast;
    private ChatLanguageModel reasoning;
    private TokenUsage usage = TokenUsage.NONE;

    public ClaudeClient(SpotterConfig config) {
        if (config == null) {
            throw new IllegalArgumentException(
                    "Configuration is required");
        }
        this.config = config;
    }

    @Override
    public String name() {
        return "claude";
    }

    @Override
    public TokenUsage totalUsage() {
        return usage;
    }

    @Override
    public LLMResponse complete(
            LLMRequest request) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "A request is required");
        }
        try {
            ChatLanguageModel model =
                    modelFor(request.tier());

            List<ChatMessage> messages =
                    new ArrayList<>();
            if (!request.systemPrompt()
                    .isBlank()) {
                messages.add(SystemMessage.from(
                    request.systemPrompt()));
            }
            messages.add(UserMessage.from(
                    request.userPrompt()));

            Response<AiMessage> response =
                request.hasTools()
                    ? model.generate(messages,
                        toolsFor(request))
                    : model.generate(messages);

            return convert(response);

        } catch (ConfigurationException e) {
            throw new LLMException(
                    e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new LLMException(
                "The model could not be"
                + " reached: " + e.getMessage(),
                e);
        }
    }

    private LLMResponse convert(
            Response<AiMessage> response) {
        AiMessage message = response.content();
        TokenUsage used = usageOf(response);
        usage = usage.plus(used);

        List<ToolCall> calls = new ArrayList<>();
        if (message.hasToolExecutionRequests()) {
            for (ToolExecutionRequest requested
                    : message
                        .toolExecutionRequests()) {
                calls.add(new ToolCall(
                    requested.id(),
                    requested.name(),
                    argumentsOf(
                        requested.arguments())));
            }
        }
        return new LLMResponse(message.text(),
                calls, used);
    }

    private TokenUsage usageOf(
            Response<AiMessage> response) {
        var reported = response.tokenUsage();
        if (reported == null) {
            return TokenUsage.NONE;
        }
        Integer in = reported.inputTokenCount();
        Integer out = reported.outputTokenCount();
        return new TokenUsage(
                in == null ? 0 : in,
                out == null ? 0 : out);
    }

    /**
     * Tool arguments arrive as a JSON object.
     * Values are flattened to text because that
     * is all a tool needs, and an unreadable
     * argument list becomes an empty one rather
     * than an exception: the tool then reports
     * what it was missing, which the model can
     * act on.
     */
    private Map<String, String> argumentsOf(
            String raw) {
        Map<String, String> arguments =
                new HashMap<>();
        if (raw == null || raw.isBlank()) {
            return arguments;
        }
        try {
            JsonNode node = json.readTree(raw);
            Iterator<String> names =
                    node.fieldNames();
            while (names.hasNext()) {
                String field = names.next();
                arguments.put(field,
                    node.get(field).asText());
            }
        } catch (Exception e) {
            return Map.of();
        }
        return arguments;
    }

    private List<ToolSpecification> toolsFor(
            LLMRequest request) {
        List<ToolSpecification> specs =
                new ArrayList<>();
        for (ToolSchema schema
                : request.tools()) {
            ToolSpecification.Builder builder =
                ToolSpecification.builder()
                    .name(schema.name())
                    .description(
                        schema.description());
            for (Map.Entry<String, String> param
                    : schema.parameters()
                        .entrySet()) {
                builder.addParameter(
                    param.getKey(),
                    JsonSchemaProperty.STRING,
                    JsonSchemaProperty.description(
                        param.getValue()));
            }
            specs.add(builder.build());
        }
        return specs;
    }

    private ChatLanguageModel modelFor(
            ModelTier tier) {
        if (tier == ModelTier.REASONING) {
            if (reasoning == null) {
                reasoning = build(
                    config.reasoningModel());
            }
            return reasoning;
        }
        if (fast == null) {
            fast = build(config.model());
        }
        return fast;
    }

    private ChatLanguageModel build(
            String modelName) {
        return AnthropicChatModel.builder()
            .apiKey(config.requireApiKey())
            .modelName(modelName)
            .maxTokens(config.maxTokens())
            .timeout(Duration.ofSeconds(
                config.timeoutSeconds()))
            .build();
    }
}
