package ro.mindit.mindshopper.chatbot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.aad.adal4j.AuthenticationException;
import com.microsoft.bot.connector.ConnectorClient;
import com.microsoft.bot.connector.authentication.CredentialProvider;
import com.microsoft.bot.connector.authentication.CredentialProviderImpl;
import com.microsoft.bot.connector.authentication.MicrosoftAppCredentials;
import com.microsoft.bot.connector.implementation.ConnectorClientImpl;
import com.microsoft.bot.schema.models.Activity;
import com.microsoft.rest.credentials.ServiceClientCredentials;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HTTP servlet that handles incoming HTTP messages by directing them to the chatbot implementation.
 */
public final class ChatBotServlet extends HttpServlet {
    private static Logger logger = Logger.getLogger(ChatBotServlet.class.getName());

    private final ObjectMapper objectMapper;
    private final CredentialProvider credentialProvider;
    private final ServiceClientCredentials clientCredentials;
    private final ChatBot bot;

    /**
     * Initializes new instance of {@link ChatBotServlet}
     */
    public ChatBotServlet() {
        this(new ChatBotImpl());
    }

    public ChatBotServlet(ChatBot bot) {
        this.credentialProvider = new CredentialProviderImpl(getAppId(), getKey());
        this.objectMapper = ObjectMapperFactory.createObjectMapper();
        this.clientCredentials = new MicrosoftAppCredentials(getAppId(), getKey());
        this.bot = bot;
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);

        bot.init(config.getServletContext());
    }

    /**
     * Handles HTTP POST requests
     *
     * @param request  Incoming HTTP request
     * @param response Outgoing HTTP response
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        try {
            String authorizationHeader = request.getHeader("Authorization");
            Activity activity = deserializeActivity(request);

            // The outgoing messages are not sent as a reply to the incoming HTTP request.
            // Instead you create a separate channel for them.
            ConnectorClient connectorInstance = new ConnectorClientImpl(activity.serviceUrl(), clientCredentials);
            ConversationContext context = new ConversationContextImpl(connectorInstance, activity);

            bot.handle(context);

            // Always send a HTTP 202 notifying the bot framework channel that we've handled the incoming request.
            response.setStatus(202);
            response.setContentLength(0);
        } catch (AuthenticationException ex) {
            logger.log(Level.WARNING, "User is not authenticated", ex);
            writeJsonResponse(response, 401, new ApplicationError("Unauthenticated request"));
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Failed to process incoming activity", ex);
            writeJsonResponse(response, 500, new ApplicationError("Failed to process request"));
        }
    }

    /**
     * Writes a JSON response
     *
     * @param response   Response object to write to
     * @param statusCode Status code for the request
     * @param value      Value to write
     */
    private void writeJsonResponse(HttpServletResponse response, int statusCode, Object value) {
        response.setContentType("application/json");
        response.setStatus(statusCode);

        try (PrintWriter writer = response.getWriter()) {
            objectMapper.writeValue(writer, value);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Failed to serialize object to output stream", ex);
        }
    }

    /**
     * Deserializes the request body to a chatbot activity
     *
     * @param request Request object to read from
     * @return Returns the deserialized request
     * @throws IOException Gets thrown when the activity could not be deserialized
     */
    private Activity deserializeActivity(HttpServletRequest request) throws IOException {
        return objectMapper.readValue(request.getReader(), Activity.class);
    }

    /**
     * Gets the bot app ID
     *
     * @return The bot app ID
     */
    private String getAppId() {
        String appId = System.getenv("BOT_APPID");

        if (appId == null) {
            return "";
        }

        return appId;
    }

    /**
     * Gets the bot password
     *
     * @return The bot password
     */
    private String getKey() {
        String key = System.getenv("BOT_KEY");

        if (key == null) {
            return "";
        }

        return key;
    }
}