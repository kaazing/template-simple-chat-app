/* Processing statement for eslint, ignore: */
/* global console Charset Uint8Array Blob WebSocketFactory ArrayBuffer ByteBuffer BlobUtils */

const ChatConnection = function(connectionOptions) {

    const options = connectionOptions;

    let ws;

    const hexChar = ["0", "1", "2", "3", "4", "5", "6", "7","8", "9", "A", "B", "C", "D", "E", "F"];

    // We want these to be global so
    // we can process multiple frames into them.
    // Message buffer.
    let messageBuffer = new ByteBuffer();

    // Remaining length of message to read.
    let remainingLen = 0;

    // Multiple message buffer to pass fragments left over from event data processing.
    let _buf = new ByteBuffer();

    const debugLog = function(str) {
        if (options.debug) {
            console.log('[PL] ', str);
        }
    };

    const arrayBufferToString = function(buf) {
        return String.fromCharCode.apply(null, new Uint8Array(buf));
    };

    const pad = function(n, width, z) {
        z = z || '0';
        n = n + '';
        return n.length >= width ? n : new Array(width - n.length + 1).join(z) + n;
    };

    const byteToHex = function(b) {
        return hexChar[(b >> 4) & 0x0f] + hexChar[b & 0x0f];
    };

    // TODO: Rename to not use "print"
    const byteArrayToHexArray = function(data) {
        let hex = "";

        const b = new ByteBuffer(data);

        while (b.hasRemaining()) {
            const byt = b.get();
            hex = hex + byteToHex(byt) + " ";
        }

        // debugLog("Received data (as hex):");
        return hex;
        // debugLog('  ' + hex);
    };

    // TODO: Delete?
    const debugBuf = function(str, buf) {
        debugLog(str + "position: " + buf.position + " limit: " + buf.limit);
    };

    // The built-in one doesn't work always.
    const myIsNan = function(num) {
        for (let i = 0; i < num.length; i++) {
            const c = num[i];
            if ((c < 48) || (c > 57)) {
                return true;
            }
        }

        return false;
    };

    const processFrame = function(frame) {

        debugLog('ChatConnection.processFrame() New bytes:');
        debugLog('  ' + byteArrayToHexArray(frame));

        // Flag for when a single frame contains more than one message
        let dataRemainsInBufToBeProcessed = false;

        const bufHasData = _buf.limit > 0;
        if (bufHasData) {
            debugLog('ChatConnection.processFrame() buf contains bytes from previous frame:');
            debugLog('  ' + byteArrayToHexArray(_buf.array));
        }

        // Add the data to the buffer, appending to any left-over fragment from last onMessage data.
        // Make sure to append the new bytes.
        _buf.skip(_buf.remaining());
        _buf.putBytes(frame);

        // Prepare it for reading
        _buf.flip();

        if (bufHasData) {
            debugLog('ChatConnection.processFrame() Combined buf contents:');
            debugLog('  ' + byteArrayToHexArray(_buf.array));
        }

        do {
            // If we're starting a new message, wait for the first four bytes that make up
            // the length prefix.
            if (remainingLen === 0 && _buf.limit > 4) {
                const lenBytes = _buf.getBytes(4);
                if (myIsNan(lenBytes)) {
                    console.log('ERROR: Received invalid bytes. The first four bytes should be the length prefix.');
                    continue;
                } else {
                    // We have a length; initialize
                    // Global scope variables that will be re-used if the message in question
                    // consists of multiple WebSocket frames.
                    messageBuffer = new ByteBuffer();
                    // remainingLen = 0;

                    const lengthStr = new ByteBuffer(lenBytes).getString(Charset.UTF8);
                    debugLog('ChatConnection.processFrame() Found message length prefix: ' + lengthStr);
                    remainingLen = parseInt(lengthStr);
                    _buf.compact();
                }
            }

            if (remainingLen === 0) {
                // We have looped back around.
                // There is a fragment left in _buf to which we will
                // append the next event's data.
                dataRemainsInBufToBeProcessed = false;
                debugLog('ChatConnection.processFrame() Haven\'t got the prefix yet. Waiting for more bytes.');
            } else {
                // We are continuing processing.
                // Make sure we append any new data.
                messageBuffer.skip(messageBuffer.remaining());

                let bytesRead = 0;

                if (remainingLen >= _buf.limit) {
                    // We need everything in the messageBuffer.
                    messageBuffer.putBuffer(_buf);
                    bytesRead = _buf.limit;
                    _buf = new ByteBuffer();
                    dataRemainsInBufToBeProcessed = false;
                    debugLog('ChatConnection.processFrame() Message not complete yet. Waiting for more bytes.');
                } else {
                    // We only want to read remainingLen bytes
                    // and save the rest for the next go around
                    const fragment = _buf.getBytes(remainingLen);
                    _buf.compact();
                    messageBuffer.putBytes(fragment);
                    bytesRead = remainingLen;
                    dataRemainsInBufToBeProcessed = true;
                    debugLog('ChatConnection.processFrame() ==================> Finished message, but bytes remain. They must be for the next message.');
                }

                remainingLen = remainingLen - bytesRead;

                // Prepare the messageBuffer for reading
                messageBuffer.flip();

                if (remainingLen === 0) {
                    // We have the entire JSON message in the messageBuffer
                    const jsonMsg = messageBuffer.getString(Charset.UTF8);
                    messageBuffer.compact();
                    debugLog('ChatConnection.processFrame() Full message: ' + jsonMsg);
                    const json = JSON.parse(jsonMsg);

                    let applicationMessage;
                    switch (json.type) {

                        case 'connected':
                            applicationMessage = {
                                "username": json.username,
                                "role": json.role
                            };
                            options.handleUserConnected(applicationMessage);
                            break;

                        case 'disconnected':
                            applicationMessage = {
                                "username": json.username
                            };
                            options.handleUserDisconnected(applicationMessage);
                            break;

                        case 'NoAgentsPresent':
                            options.handleNoAgentsPresent();
                            break;

                        case 'AgentsPresent':
                            options.handleAgentsPresent();
                            break;

                        case 'customerMessage':
                            applicationMessage = {
                                "sender": json.sender,
                                "messageText": json.messageText
                            };
                            options.handleCustomerMessage(applicationMessage);
                            break;

                        case 'agentMessage':
                            // TODO: Make the same as the customerMessage case
                            applicationMessage = {
                                "sender": json.sender,
                                "to": json.to,
                                "messageText": json.messageText
                            };
                            options.handleAgentMessage(applicationMessage);
                            break;

                        default:
                            options.handleError('Received invalid message: ' + jsonMsg);
                            return;

                    }
                }
            }

        } while (dataRemainsInBufToBeProcessed);

        // Else onMessage will continue processing.
        debugBuf("ChatConnection.processFrame() After processing: ", _buf);

    };

    const onOpen = function() {
        debugLog('ChatConnection.onOpen() Connected');
        options.handleOpen();
    };

    const onClose = function(event) {
        debugLog('ChatConnection.onClose() Disconnected: code=' + event.code);
        options.handleClose(event.code);
    };

    const onError = function(event) {
        debugLog('ChatConnection.onError() ERROR: ' + event.data);
        options.handleError(event.data);
    };

    const bin2String = function(array) {
        return String.fromCharCode.apply(String, array);
    };

    const onMessage = function(event) {
        debugLog('ChatConnection.onMessage() Received a WebSocket frame');

        const callback = function(result) {

            if (options.debug) {
                if (event.data instanceof Blob) {

                    debugLog('  Type:      Blob');
                    debugLog('  Dec array: ' + result);
                    debugLog('  Hex array: ' + byteArrayToHexArray(result));
                    debugLog('  String:    ' + bin2String(result));

                } else if (event.data instanceof ArrayBuffer) {

                    debugLog('  Type:   ArrayBuffer');
                    debugLog('  String: ' + arrayBufferToString(event.data));

                } else if (typeof event.data === 'string') {

                    debugLog('  Type:  String');
                    debugLog('  Frame: ' + event.data);

                } else {

                    debugLog('  Type:  Unknown');
                    debugLog('  Frame: ' + event.data);

                }
            }

            processFrame(result);
        };
        BlobUtils.asNumberArray(callback, event.data);
    };

    const doSend = function(jsonMessage) {
        const msgStr = JSON.stringify(jsonMessage);
        const len = pad(msgStr.length, 4);
        debugLog('ChatConnection.doSend() ' + len + msgStr);

        const frame = new ByteBuffer();

        frame.putString(len + msgStr, Charset.UTF8);

        // flip the frame buffer
        frame.flip();
        ws.send(frame);
    };

    ChatConnection.prototype.connect = function() {
        const factory = new WebSocketFactory();
        ws = factory.createWebSocket(options.url);
        debugLog('ChatConnection.connect() Connecting to ' + options.url);
        ws.onopen = function(event) { onOpen(event); };
        ws.onclose = function(event) { onClose(event); };
        ws.onerror = function(event) { onError(event); };
        ws.onmessage = function(event) { onMessage(event); };
    };

    ChatConnection.prototype.disconnect = function() {
        debugLog('ChatConnection.disconnect() Disconnecting');
        ws.close();
    };

    // {"type":"credentials","username":"joe","role":"customer"}
    ChatConnection.prototype.sendCredentials = function(username, role) {
        debugLog('ChatConnection.sendCredentials() Sending username=[' + username + '], role=[' + role + ']');

        const msg = {
            "type": "credentials",
            "username": username,
            "role": role
        };
        doSend(msg);
    };

    // The to parameter is optional. Customers don't need to specify a to.
    // {"type":"send","message":"Hello, world!"}
    ChatConnection.prototype.sendMessage = function(messageStr, to) {
        debugLog('ChatConnection.sendMessage() Sending: [' + messageStr + ']');

        const msg = {
            "type": "message",
            "messageText": messageStr
        };

        if (typeof to !== "undefined") {
            debugLog('                             To: ' + to);
            msg.to = to;
        }

        doSend(msg);
    };

};
