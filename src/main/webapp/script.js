const headerElement = document.querySelector("[data-info]")
const boardElement = document.querySelector("[data-board]")
const infoElement = document.querySelector("[data-turn]")

const baseURL = "ws://localhost:8080/talpa"

const players = {PLAYER_1: ["blue", "Blue-Circle.svg"], PLAYER_2: ["red", "Red-Circle.svg"]}
let turn = null
let player = null
let opponent = null
let originTile = null
let destinyTile = null
let boardLength = null
let boardChildren = null
let playerPiecesCoordinates = []
let isPossibleToMovePiece = true

const websocket = new WebSocket(baseURL)

websocket.onopen = startInteraction
websocket.onmessage = onMessage

function startInteraction() {
    document.addEventListener("click", handleMouseClick)
    const startButton = document.createElement("button")
    startButton.type = "button"
    startButton.textContent = "Iniciar"
    startButton.addEventListener("click", startGame)
    headerElement.appendChild(startButton)
}

function startGame() {
    const request = JSON.stringify({connectionType: "ENTER_GAME"})
    websocket.send(request)
    headerElement.lastChild.remove()
}

function onMessage(evt) {
    const response = JSON.parse(evt.data)
    const gameStatus = response["status"]
    let board = null
    switch (gameStatus) {
        case "WAITING_PLAYERS":
            player = response["player"]
            opponent = player === "PLAYER_1" ? "PLAYER_2" : "PLAYER_1"

            const waitingTitle = document.createElement("h4")
            waitingTitle.textContent = "Waiting your opponent..."
            headerElement.appendChild(waitingTitle)

            break
        case "IN_PROGRESS":
            removeChildren(headerElement)
            removeChildren(infoElement)

            board = response["board"]
            boardLength = board.length
            constructBoard(board)

            turn = response["turn"]

            const playerTitle = document.createElement("h4")
            playerTitle.textContent = `You're the ${players[player][0]} player!`
            headerElement.appendChild(playerTitle)

            if (turn === player) {
                isPossibleToMovePiece = isPossibleToPlay()
                if (!isPossibleToMovePiece) {
                    const removePieceInfo = document.createElement("h4")
                    removePieceInfo.textContent =
                        "It's your turn but there is no possible move. Select one piece to remove from board."
                    infoElement.appendChild(removePieceInfo)
                    break
                }
            }

            const turnInfo = document.createElement("h4")
            turnInfo.textContent = turn === player ? `It's your turn!` : `It's opponent's turn...`
            infoElement.appendChild(turnInfo)

            break
        case "FINISHED":
            removeChildren(headerElement)

            const winner = response["winner"]

            const endGameMessageElement = document.createElement("h4")
            if (winner === player) {
                endGameMessageElement.textContent = "Congratulations, you win!"
            } else if (winner === opponent) {
                endGameMessageElement.textContent = "Oh...You lost!"
            }

            headerElement.appendChild(endGameMessageElement)

            board = response["board"]
            boardLength = board.length

            constructBoard(board)

            // draw winner line
            const winnerPaths = response["winnerPaths"]
            drawWinnerPath(winnerPaths)

            stopInteraction()
            break
    }
}

function constructBoard(board) {
    removeChildren(boardElement)
    playerPiecesCoordinates = []
    for (let row = 0; row < boardLength; row++) {
        for (let column = 0; column < boardLength; column++) {
            const tileStatus = board[row][column]

            if (tileStatus === player) { playerPiecesCoordinates.push([row, column]) }

            const tile = document.createElement("div")
            tile.className = "tile"
            tile.dataset.index = (boardLength * row + column).toString()

            if (tileStatus === "EMPTY") {
                tile.dataset.state = "empty"
            } else if (tileStatus === "PLAYER_1" || tileStatus === "PLAYER_2") {
                tile.dataset.state = players[tileStatus][0]
                const image = document.createElement("img")
                image.src = `images/${players[tileStatus][1]}`
                image.className = "piece"
                tile.appendChild(image)
            }

            boardElement.appendChild(tile)
        }
    }
    boardChildren = Array.of(boardElement.children).at(0)
}

function handleMouseClick(evt) {
    const target = evt.target
    if (target.className === "tile") {
        handlePieceClick(target)
    } else if (target.className === "piece") {
        handlePieceClick(target.parentNode)
    }
}

function handlePieceClick(tile) {
    if (!isPossibleToMovePiece) {
        removePieceFromBoard(tile)
        return
    }
    const isItMyTurn = player === turn
    if (!isItMyTurn) {
        removeChildren(infoElement)
        const opponentsTurnWarning = document.createElement("h4")
        opponentsTurnWarning.textContent = "Wait for your turn!"
        infoElement.appendChild(opponentsTurnWarning)
        return
    }
    const isClickedPieceMine = tile.dataset.state === players[player][0]
    const isClickedPieceEmpty = tile.dataset.state === "empty"
    if (originTile === null && isClickedPieceMine) {
        originTile = tile
    } else if (originTile === null && !isClickedPieceMine) {
        removeChildren(infoElement)
        const pieceNotMineWarning = document.createElement("h4")
        pieceNotMineWarning.textContent = "Origin should be one of your pieces."
        infoElement.appendChild(pieceNotMineWarning)
    } else if (destinyTile === null && (isClickedPieceMine || isClickedPieceEmpty)) {
        removeChildren(infoElement)
        const pieceMineWarning = document.createElement("h4")
        pieceMineWarning.textContent = "Destiny should be an opponent's piece."
        infoElement.appendChild(pieceMineWarning)
    } else if (destinyTile === null && !isClickedPieceMine && !isClickedPieceEmpty) {
        const originCoordinates = getTileCoordinates(originTile)
        const destinyCoordinates = getTileCoordinates(tile)
        if (!areCoordinatesAdjacents(originCoordinates, destinyCoordinates)) {
            removeChildren(infoElement)
            const pieceMineWarning = document.createElement("h4")
            pieceMineWarning.textContent = "Origin and destiny should be adjacents."
            infoElement.appendChild(pieceMineWarning)
            originTile = null
            return;
        }
        destinyTile = tile
        const request = JSON.stringify({
            play: {
                origin: { x: originCoordinates[0], y: originCoordinates[1] },
                destiny: { x: destinyCoordinates[0], y: destinyCoordinates[1] }
            },
            connectionType: "PLAY"
        })
        websocket.send(request)
        originTile = destinyTile = null
    }
}

function removePieceFromBoard(tile) {
    if (isOpponentPiece(tile)) {
        removeChildren(infoElement)
        const pieceMineWarning = document.createElement("h4")
        pieceMineWarning.textContent = "You can't remove an opponent piece."
        infoElement.appendChild(pieceMineWarning)
        return
    }
    const tileCoordinate = getTileCoordinates(tile)
    const request = JSON.stringify({
        removeCoordinate: { x: tileCoordinate[0], y: tileCoordinate[1] },
        connectionType: "REMOVE_PIECE"
    })
    websocket.send(request);
}

function removeChildren(node) {
    while (node.firstChild) {
        node.removeChild(node.firstChild)
    }
}

function getTileCoordinates(tile) {
    for (let index = 0; index < boardLength * boardLength; index++) {
        const currentTile = boardChildren[index]
        if (currentTile.dataset.index === tile.dataset.index) {
            const rowIndex = ~~(index / boardLength)
            const columnIndex = index % boardLength
            return [rowIndex, columnIndex]
        }
    }
    return null
}

function areCoordinatesAdjacents(origin, destiny) {
    return origin[0] === destiny[0] && Math.abs(origin[1] - destiny[1]) === 1 ||
        origin[1] === destiny[1] && Math.abs(origin[0] - destiny[0]) === 1;
}

function isPossibleToPlay() {
    for (let index = 0; index < playerPiecesCoordinates.length; index++) {
        const currentPiece = playerPiecesCoordinates[index]
        if (currentPiece[0] !== 0) {
            const abovePieceIndex = (currentPiece[0] - 1) * boardLength + currentPiece[1]
            if (isOpponentPiece(boardChildren[abovePieceIndex])) return true
        }
        if (currentPiece[0] !== boardLength - 1) {
            const belowPieceIndex = (currentPiece[0] + 1) * boardLength + currentPiece[1]
            if (isOpponentPiece(boardChildren[belowPieceIndex])) return true
        }
        if (currentPiece[1] !== 0) {
            const leftPieceIndex = currentPiece[0] * boardLength + currentPiece[1] - 1
            if (isOpponentPiece(boardChildren[leftPieceIndex])) return true
        }
        if (currentPiece[1] !== boardLength - 1) {
            const rightPieceIndex = currentPiece[0] * boardLength + currentPiece[1] + 1
            if (isOpponentPiece(boardChildren[rightPieceIndex])) return true
        }
    }
    return false
}

function isOpponentPiece(piece) {
    const pieceColor = piece.dataset.state
    return players[opponent][0] === pieceColor
}

function drawWinnerPath(winnerPaths) {
    const boardChildren = boardElement.children
    const winnerPath = winnerPaths[0]
    for (let coordinateIndex = 0; coordinateIndex < winnerPath.length; coordinateIndex++) {
        const tileIndex = winnerPath[coordinateIndex]["x"] * boardLength + winnerPath[coordinateIndex]["y"]
        const tile = boardChildren.item(tileIndex)
        const pathPositionElement = document.createElement("h3")
        pathPositionElement.textContent = (coordinateIndex + 1).toString()
        tile.appendChild(pathPositionElement)
    }
}

function stopInteraction() {
    document.removeEventListener("click", handleMouseClick)
    const playAgainButton = document.createElement("button")
    playAgainButton.type = "button"
    playAgainButton.textContent = "Play Again!"
    playAgainButton.addEventListener("click", () => location.reload())
    removeChildren(infoElement)
    infoElement.appendChild(playAgainButton)
}
