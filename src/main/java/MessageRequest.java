import lombok.Getter;
import lombok.Setter;
import model.Coordinate;
import model.Play;

@Getter
@Setter
public class MessageRequest {
    private Play play;
    private Coordinate removeCoordinate;
    private ConnectionType connectionType;
}
