import lombok.Getter;
import lombok.Setter;
import model.*;

import java.util.List;

@Getter
@Setter
public class MessageResponse {
    private CellState[][] board;
    private Player player;
    private Player turn;
    private Winner winner;
    private GameStatus status;
    private List<List<Coordinate>> winnerPaths;

    // After enter game:
    public MessageResponse(Player player, GameStatus status) {
        this.player = player;
        this.status = status;
    }

    // After make a move:
    public MessageResponse(CellState[][] board, Player turn, GameStatus status) {
        this.board = board;
        this.turn = turn;
        this.status = status;
    }

    // After opponent quit:
    public MessageResponse(Winner winner, GameStatus status) {
        this.winner = winner;
        this.status = status;
    }

    // After someone win the game:
    public MessageResponse(CellState[][] board, Winner winner, GameStatus status, List<List<Coordinate>> winnerPaths) {
        this.board = board;
        this.winner = winner;
        this.status = status;
        this.winnerPaths = winnerPaths;
    }
}
