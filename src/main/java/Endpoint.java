import model.*;

import javax.websocket.EncodeException;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

@ServerEndpoint(value = "/talpa", encoders = MessageEncoder.class, decoders = MessageDecoder.class)
public class Endpoint {

    private static final Game game = new Game();

    @OnOpen
    public void onOpen(Session session) {
        System.out.println("Connection started with ID: " + session.getId());
    }

    @OnMessage
    public void onMessage(Session session, MessageRequest incomingMessage) throws IOException, EncodeException {
        switch (incomingMessage.getConnectionType()) {
            case ENTER_GAME -> handleEnterGameRequest(session);
            case PLAY -> handlePlayRequest(session, incomingMessage);
            case REMOVE_PIECE -> handleRemovePieceRequest(session, incomingMessage);
        }
    }

    @OnClose
    public void onClose(Session session) throws IOException, EncodeException {
        if (game.getPlayers().size() == 2) {
            final int playerIndex = game.getPlayers().indexOf(session);
            game.removePlayer(session);
            final Session winnerSession = game.getPlayers().get(0);
            final Winner winnerPlayer = playerIndex == 0 ? Winner.PLAYER_2 : Winner.PLAYER_1;
            winnerSession.getBasicRemote()
                    .sendObject(new MessageResponse(winnerPlayer, GameStatus.FINISHED));
        }
        game.restart();
    }

    private void sendMessageForPlayers(MessageResponse response) throws EncodeException, IOException {
        for (Session player : game.getPlayers()) {
            player.getBasicRemote().sendObject(response);
        }
    }

    private void handleEnterGameRequest(Session session) throws EncodeException, IOException {
        final boolean isRoomFull = game.getStatus() != GameStatus.WAITING_PLAYERS;
        if (isRoomFull) return;

        game.addPlayer(session);

        final int numOfPlayersInRoom = game.getPlayers().size();
        final Player playerAssigned = numOfPlayersInRoom == 1 ? Player.PLAYER_1 : Player.PLAYER_2;

        session.getBasicRemote().sendObject(new MessageResponse(playerAssigned, game.getStatus()));

        if (numOfPlayersInRoom < 2) return;

        game.setStatus(GameStatus.IN_PROGRESS);

        final MessageResponse response = new MessageResponse(game.getBoard(), game.getTurn(), game.getStatus());
        sendMessageForPlayers(response);
    }

    private void handlePlayRequest(Session session, MessageRequest incomingMessage) throws EncodeException, IOException {
        final Player currentPlayer = getCurrentPlayer(session);
        final Play playRequested = incomingMessage.getPlay();

        if (currentPlayer != game.getTurn() || !isPlayValid(playRequested)) return;

        game.executePlay(currentPlayer, playRequested);

        sendMoveResultForPlayers(currentPlayer);
    }

    private void handleRemovePieceRequest(Session session, MessageRequest incomingRequest) throws EncodeException, IOException {
        final Player currentPlayer = getCurrentPlayer(session);
        if (currentPlayer == game.getTurn()) {
            final Coordinate pieceCoordinates = incomingRequest.getRemoveCoordinate();
            game.removePiece(pieceCoordinates);

            sendMoveResultForPlayers(currentPlayer);
        }
    }

    private void sendMoveResultForPlayers(Player currentPlayer) throws EncodeException, IOException {
        final List<Coordinate> verticalWinnerPath = game.findAnyWinnerPath(Orientation.VERTICALLY);
        final List<Coordinate> horizontalWinnerPath = game.findAnyWinnerPath(Orientation.HORIZONTALLY);

        MessageResponse response;

        if (verticalWinnerPath == null && horizontalWinnerPath == null) {
            final Player opponent = currentPlayer == Player.PLAYER_1 ? Player.PLAYER_2 : Player.PLAYER_1;
            game.setTurn(opponent);
            response = new MessageResponse(game.getBoard(), game.getTurn(), game.getStatus());
        } else if (horizontalWinnerPath == null) {
            // First player won
            game.setStatus(GameStatus.FINISHED);
            response = new MessageResponse(
                    game.getBoard(),
                    Winner.PLAYER_1,
                    game.getStatus(),
                    Collections.singletonList(verticalWinnerPath)
            );
        } else {
            // Second player won
            game.setStatus(GameStatus.FINISHED);
            final List<List<Coordinate>> winnerPaths = verticalWinnerPath == null
                    ? Collections.singletonList(horizontalWinnerPath)
                    : List.of(horizontalWinnerPath, verticalWinnerPath);
            response = new MessageResponse(
                    game.getBoard(),
                    Winner.PLAYER_2,
                    game.getStatus(),
                    winnerPaths
            );
        }

        sendMessageForPlayers(response);

        if (horizontalWinnerPath != null || verticalWinnerPath != null) game.restart();
    }

    private Player getCurrentPlayer(Session session) {
        final int playerPosition = game.getPlayers().indexOf(session);
        return playerPosition == 0 ? Player.PLAYER_1 : Player.PLAYER_2;
    }

    private boolean isPlayValid(Play play) {
        return areOriginAndDestinyHorizontallyAdjacent(play) || areOriginAndDestinyVerticallyAdjacent(play);
    }

    private boolean areOriginAndDestinyHorizontallyAdjacent(Play play) {
        final boolean areTheyAtSameRow = play.origin().x() == play.destiny().x();
        return areTheyAtSameRow && Math.abs(play.origin().y() - play.destiny().y()) == 1;
    }

    private boolean areOriginAndDestinyVerticallyAdjacent(Play play) {
        final boolean areTheyAtSameColumn = play.origin().y() == play.destiny().y();
        return areTheyAtSameColumn && Math.abs(play.origin().x() - play.destiny().x()) == 1;
    }

}
