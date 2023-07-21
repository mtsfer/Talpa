package model;

import lombok.Getter;
import lombok.Setter;

import javax.websocket.Session;
import java.util.*;

@Getter
@Setter
public class Game {
    public static final int BOARD_SIZE = 8;
    private static final int[][] DIRECTIONS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    private List<Session> players;
    private CellState[][] board;
    private Player turn;
    private GameStatus status;

    public Game() {
        init();
    }

    private void init() {
        this.players = new ArrayList<>();
        this.board = new CellState[BOARD_SIZE][BOARD_SIZE];
        this.turn = Player.PLAYER_1;
        this.status = GameStatus.WAITING_PLAYERS;

        fillBoardWithInitialState();
    }

    private void fillBoardWithInitialState() {
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int column = 0; column < BOARD_SIZE; column++) {
                final boolean isFirstPlayerCell = (row + column) % 2 == 0;
                this.board[row][column] = isFirstPlayerCell ? CellState.PLAYER_1 : CellState.PLAYER_2;
            }
        }
    }

    public void executePlay(Player player, Play play) {
        final CellState cellState = player == Player.PLAYER_1 ? CellState.PLAYER_1 : CellState.PLAYER_2;
        this.board[play.destiny().x()][play.destiny().y()] = cellState;
        this.board[play.origin().x()][play.origin().y()] = CellState.EMPTY;
    }

    public void removePiece(Coordinate pieceCoordinate) {
        final CellState pieceState = board[pieceCoordinate.x()][pieceCoordinate.y()];
        if ((turn == Player.PLAYER_1 && pieceState == CellState.PLAYER_1) ||
                (turn == Player.PLAYER_2 && pieceState == CellState.PLAYER_2)) {
            board[pieceCoordinate.x()][pieceCoordinate.y()] = CellState.EMPTY;
        }
    }

    public List<Coordinate> findAnyWinnerPath(Orientation orientation) {
        final List<Coordinate> possibleOrigins = new ArrayList<>();
        final List<Coordinate> possibleDestinys = new ArrayList<>();

        if (orientation == Orientation.VERTICALLY) {
            for (int i = 0; i < BOARD_SIZE; i++) {
                if (board[0][i] == CellState.EMPTY) {
                    possibleOrigins.add(new Coordinate(0, i));
                }
                if (board[BOARD_SIZE - 1][i] == CellState.EMPTY) {
                    possibleDestinys.add(new Coordinate(BOARD_SIZE - 1, i));
                }
            }
        } else if (orientation == Orientation.HORIZONTALLY) {
            for (int i = 0; i < BOARD_SIZE; i++) {
                if (board[i][0] == CellState.EMPTY) {
                    possibleOrigins.add(new Coordinate(i, 0));
                }
                if (board[i][BOARD_SIZE - 1] == CellState.EMPTY) {
                    possibleDestinys.add(new Coordinate(i, BOARD_SIZE - 1));
                }
            }
        }

        for (Coordinate origin : possibleOrigins) {
            for (Coordinate destiny : possibleDestinys) {
                List<Coordinate> shortestPath = findShortestPath(origin, destiny, orientation);
                if (shortestPath != null) return shortestPath;
            }
        }

        return null;
    }

    private List<Coordinate> findShortestPath(Coordinate origin, Coordinate destiny, Orientation orientation) {
        final Queue<Coordinate> queue = new LinkedList<>();
        final boolean[][] visited = new boolean[BOARD_SIZE][BOARD_SIZE];
        final int[][] parent = new int[BOARD_SIZE][BOARD_SIZE];

        queue.add(origin);
        visited[origin.x()][origin.y()] = true;

        while (!queue.isEmpty()) {
            final Coordinate current = queue.poll();
            final int x = current.x();
            final int y = current.y();

            if (x == destiny.x() && y == destiny.y()) {
                return reconstructPath(parent, destiny, orientation);
            }

            for (int[] direction : DIRECTIONS) {
                final int newX = x + direction[0];
                final int newY = y + direction[1];
                final Coordinate newCoordinate = new Coordinate(newX, newY);

                if (isCoordinateValid(newCoordinate) && !visited[newX][newY]) {
                    queue.add(newCoordinate);
                    visited[newX][newY] = true;
                    parent[newX][newY] = x * BOARD_SIZE + y;
                }
            }
        }

        return null;
    }

    private List<Coordinate> reconstructPath(int[][] parent, Coordinate destiny, Orientation orientation) {
        List<Coordinate> path = new ArrayList<>();
        int x = destiny.x();
        int y = destiny.y();

        if (orientation == Orientation.VERTICALLY) {
            while (x != 0) {
                path.add(0, new Coordinate(x, y));
                int parentIndex = parent[x][y];
                x = parentIndex / BOARD_SIZE;
                y = parentIndex % BOARD_SIZE;
            }

            path.add(0, new Coordinate(0, y));
        } else if (orientation == Orientation.HORIZONTALLY) {
            while (y != 0) {
                path.add(0, new Coordinate(x, y));
                int parentIndex = parent[x][y];
                x = parentIndex / BOARD_SIZE;
                y = parentIndex % BOARD_SIZE;
            }

            path.add(0, new Coordinate(x, 0));
        }


        return path;
    }

    private boolean isCoordinateValid(Coordinate coordinate) {
        final boolean validXCoordinate = coordinate.x() >= 0 && coordinate.x() < BOARD_SIZE;
        final boolean validYCoordinate = coordinate.y() >= 0 && coordinate.y() < BOARD_SIZE;
        return validXCoordinate && validYCoordinate && this.board[coordinate.x()][coordinate.y()] == CellState.EMPTY;
    }

    public void addPlayer(Session player) {
        players.add(player);
    }

    public void restart() { init(); }

    public void removePlayer(Session player) {
        players.remove(player);
    }
}
