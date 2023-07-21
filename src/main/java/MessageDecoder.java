import com.google.gson.Gson;

import javax.websocket.Decoder;
import javax.websocket.EndpointConfig;

public class MessageDecoder implements Decoder.Text<MessageRequest> {

    private static final Gson gson = new Gson();

    @Override
    public MessageRequest decode(String s) {
        return gson.fromJson(s, MessageRequest.class);
    }

    @Override
    public boolean willDecode(String s) {
        return true;
    }

    @Override
    public void init(EndpointConfig config) {

    }

    @Override
    public void destroy() {

    }
}
