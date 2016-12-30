// TODO: Don't let an agent send until there is a customer
// TODO: Let users disconnect

/* Processing statement for eslint. Please ignore, but leave in place: */
/* global document window console localStorage getRole ChatConnection */

const DEBUG = true;

// DOM elements
let $wsUrl, $connectBut, $disconnectBut;
let $connectionStatus;
let $username;
let $users;
let $conversation;
let $sendMessage, $sendMessageBut;

let chatConnection;

// [
//     { username: "joe", messages: ["message1", "message2", ...] },
//     { username: "dan", messages: ["message1", "message2", ...] },
// ]
// [
//     {
//         username: "joe",
//         conversation: [
//             {type: "sent", sender: "joe", messageText: "Hello"},
//             {type: "received", sender: "sidda", messageText: "Hi there. How can I help you?"},
//             {type: "sent", sender: "joe", messageText: "I need help with ..."},
//             ...
//         ]
//     },
//     {
//         username: "tom",
//         conversation: [
//             {type: "sent", sender: "tom", messageText: "Yo"},
//             {type: "received", sender: "sidda", messageText: "Can I help you?"},
//             {type: "sent", sender: "tom", messageText: "Yes. Please tell me about ..."},
//             ...
//         ]
//     }
// ]
const users = [];

const debugLog = function(str) {
    if (DEBUG) {
        console.log('[SC] ', str);
    }
};

// Fill the vertical space with the conversation area.
const handleWindowResize = function() {
    const outerMargin = $('#upper').height() + $('#sendMessageDetails').height() + 60;
    const height = $(window).outerHeight() - outerMargin;
    $conversation.css('height', height + 'px');
    $('#user-list').css('height', (height) + 'px');
};

const changeWindowTitle = function() {
    let suffix = '';
    const username = $.trim($username.val());
    if (username !== '' ) {
        suffix = ' (' + username + ')';
    }
    if (getRole() === 'customer') {
        document.title = 'Simple Customer/Agent Chat App - Customer' + suffix;
    } else {
        document.title = 'Simple Customer/Agent Chat App - Agent' + suffix;
    }
};

// Update GUI elements based on the connection status.
// isConnected (boolean)
//
const updateGuiConnectedStatus = function(isConnected) {
    $wsUrl.attr('disabled', isConnected);
    $connectBut.attr('disabled', isConnected);
    $disconnectBut.attr('disabled', !isConnected);
    // $sendMessage.attr('disabled', !isConnected);
    // $sendMessageBut.attr('disabled', !isConnected);

    if (isConnected) {
        $connectionStatus.removeClass('disconnected');
        $connectionStatus.addClass('connected');
    } else {
        $connectionStatus.removeClass('connected');
        $connectionStatus.addClass('disconnected');
        // Disable the send message field if disconnected, but don't necessarily
        // enable it when connected. An agent has to be present first.
        $username.attr('disabled', false);
    }
};

const toggleConnectionDetails = function() {
    $('#connection-details').slideToggle();
};

const createConversationSentCard = function(message) {
    return $('<div>', {
        class: "card sent"
    })
        .append($('<div>', {
            class: "sender",
            text: 'Me'
        }))
        .append($('<div>', {
            class: "message",
            text: message
        }));
};

const createConversationReceivedCard = function(from, message) {
    return $('<div>', {
        class: "card received"
    })
        .append($('<div>', {
            class: "sender",
            text: from
        }))
        .append($('<div>', {
            class: "message",
            text: message
        }));
};

const scrollDivToBottom = function($div, fast) {
    let duration = 1000;
    if (fast) {
        duration = 10;
    }
    const height = $div[0].scrollHeight;
    $div.stop().animate({ scrollTop: height }, duration);
};

const addMessageCardToScreen = function($card, fast) {
    $conversation.append($card);
    scrollDivToBottom($conversation, fast);
};

const handleChatConnectionOpen = function() {
    debugLog('handleChatConnectionOpen()');
    updateGuiConnectedStatus(true);

    if ($('#connection-details').is(':visible')) {
        toggleConnectionDetails();
    }

    if ($username.val().length === 0) {
        $username.focus();
    } else {
        chatConnection.sendCredentials($username.val(), getRole());
        $sendMessage.focus();
    }
};

const handleChatConnectionClose = function(closeCode) {
    debugLog('handleChatConnectionClose() ' + closeCode);
    updateGuiConnectedStatus(false);

    $users.empty();
    $conversation.empty();
    users.length = 0;
};

const handleChatConnectionError = function(errorData) {
    debugLog('handleChatConnectionError() ' + errorData);
};

// Return a user object from the array of users.
const getUser = function(username) {
    for (let i = 0; i < users.length; i++) {
        const user = users[i];
        if (user.username === username) {
            return user;
        }
    }
    // Not found
    return null;
};

const incrementMessageCount = function(user) {
    const $badge = $('.message-count', '#' + user.username);
    $badge.text(parseInt($badge.text()) + 1);
    $badge.removeClass('read');
    $badge.addClass('unread');
};

const clearMessageCount = function(user) {
    const $badge = $('.message-count', '#' + user.username);
    $badge.text('0');
    $badge.removeClass('unread');
    $badge.addClass('read');
};

// Handle when user clicks on a different user in the user list.
const switchToUser = function(user) {
    // Remove current messages from conversation and replace with those from new user.
    $conversation.empty();

    user.conversation.forEach(function(message) {
        let $card;
        if (message.type === 'sent') {
            $card = createConversationSentCard(message.messageText);
        } else {
            $card = createConversationReceivedCard(message.sender, message.messageText);
        }
        addMessageCardToScreen($card, true);
    });

    if (user.connected) {
        $sendMessage.attr('disabled', false);
        $sendMessageBut.attr('disabled', false);
        $sendMessage.focus();
    } else {
        $sendMessage.attr('disabled', true);
        $sendMessageBut.attr('disabled', true);

    }

    clearMessageCount(user);
};

// Handle when we receive a message from a new user. Return the user object.
const addNewUser = function(username, role) {
    // New user
    const user = {
        "username": username,
        "role": role,
        "connected": true,
        "conversation": []
    };
    users.push(user);
    const $link = $('<a>', {
        class: "list-group-item",
        id: user.username,
        href: "#"
    })
        .append($('<span>', {
            class: "connection-status disconnected"
        }))
        .append(user.username)
        .append($('<span>', {
            class: "badge message-count read",
            text: "0"
        }));
    // const $link = $('<a>', {
    //     class: "list-group-item",
    //     id: user.username,
    //     text: user.username
    // })
    //     .append($('<img>', {
    //         src: "images/led_connected_16x16.png"
    //     }));
    //     // .append($('<span>', {
    //     //     class: "badge",
    //     //     text: 14
    //     // }));
    $link.click( function(event) {
        $(this).addClass('active').siblings().removeClass('active');
        const newUsername = $(this).attr('id');
        const newUser = getUser(newUsername);
        switchToUser(newUser);
        event.preventDefault();
    });
    $users.append($link);
    // If this is the first user, make them active.
    if ($users.children().length === 1) {
        $link.addClass('active');
    }
    return user;
};

// Find the active user in the $users list and return the username.
const getActiveUsername = function() {
    return $('.active', $users).attr('id');
};

// type="sent"|"received"
const addMessageToHistory = function(type, user, senderName, messageText) {
    user.conversation.push({"type": type, "sender": senderName, "messageText": messageText});
};

const handleChatConnectionUserConnected = function(newUserMessage) {
    debugLog('handleChatConnectionUserConnected() username=[' + newUserMessage.username + '], role=[' + newUserMessage.role + ']');
    // Find the user if they already exist, otherwise create them.
    let user = getUser(newUserMessage.username);
    if (user === null) {
        user = addNewUser(newUserMessage.username, newUserMessage.role);
    }

    user.connected = true;
    $('.connection-status', '#' + user.username).removeClass('disconnected');
    $('.connection-status', '#' + user.username).addClass('connected');

    // If they were the first user or they were disconnected and they are the active user, then enable the send field
    if (getActiveUsername() === user.username) {
        switchToUser(user);
    }
};

const handleChatConnectionUserDisconnected = function(disconnectedMessage) {
    debugLog('handleChatConnectionUserDisconnected() username=[' + disconnectedMessage.username + ']');
    // Find the user if they already exist, otherwise create them.
    const user = getUser(disconnectedMessage.username);
    if (user !== null) {
        // TODO: Mark users as disconnected
        user.connected = false;
        $('.connection-status', '#' + user.username).removeClass('connected');
        $('.connection-status', '#' + user.username).addClass('disconnected');
    }

    // If they are the active user, then disable the send field
    // TODO: Do this.
    if (getActiveUsername() === user.username) {
        $sendMessage.attr('disabled', true);
        $sendMessageBut.attr('disabled', true);
    }
};

const handleChatConnectionNoAgentsPresent = function() {
    debugLog('handleChatConnectionNoAgentsPresent()');
    $sendMessage.attr('disabled', true);
    $sendMessageBut.attr('disabled', true);
};

const handleChatConnectionAgentsPresent = function() {
    debugLog('handleChatConnectionAgentsPresent()');
    $sendMessage.attr('disabled', false);
    $sendMessageBut.attr('disabled', false);
};

const handleChatConnectionCustomerMessage = function(customerMessage) {
    debugLog('handleChatConnectionCustomerMessage() sender=[' + customerMessage.sender + '], message=[' + customerMessage.messageText + ']');

    const user = getUser(customerMessage.sender);
    addMessageToHistory("received", user, user.username, customerMessage.messageText);

    if (getActiveUsername() === user.username) {
        const $card = createConversationReceivedCard(customerMessage.sender, customerMessage.messageText);
        addMessageCardToScreen($card);
    } else {
        // Update the badge, if they are not the current user
        incrementMessageCount(user);
    }
};

const handleChatConnectionAgentMessage = function(agentMessage) {
    debugLog('handleChatConnectionAgentMessage() [' + agentMessage.messageText + ']');

    if (getRole() === 'customer') {
        const $card = createConversationReceivedCard(agentMessage.sender + ' (Agent)', agentMessage.messageText);
        addMessageCardToScreen($card);
    } else {

        const user = getUser(agentMessage.to);
        addMessageToHistory("received", user, agentMessage.sender, agentMessage.messageText);

        if (getActiveUsername() === user.username) {
            const $card = createConversationReceivedCard(agentMessage.sender, agentMessage.messageText);
            addMessageCardToScreen($card);
        } else {
            // Update the badge, if they are not the current user
            incrementMessageCount(user);
        }
    }

};

const handleClickConnectBut = function() {
    debugLog('Connecting');
    const connectionProperties = {
        "url": $wsUrl.val(),
        "handleOpen": handleChatConnectionOpen,
        "handleClose": handleChatConnectionClose,
        "handleError": handleChatConnectionError,
        "handleUserConnected": handleChatConnectionUserConnected,
        "handleUserDisconnected": handleChatConnectionUserDisconnected,
        "handleNoAgentsPresent": handleChatConnectionNoAgentsPresent,
        "handleAgentsPresent": handleChatConnectionAgentsPresent,
        "handleCustomerMessage": handleChatConnectionCustomerMessage,
        "handleAgentMessage": handleChatConnectionAgentMessage,
        "debug": true // Whether to write debug messages to console.log
    };
    chatConnection = new ChatConnection(connectionProperties);
    chatConnection.connect();
};

const handleClickDisconnectBut = function() {
    chatConnection.disconnect();
};

const handleClickSendMessageBut = function() {
    // TODO: Delete?
    // if ($username.val().length === 0) {
    //     debugLog('First enter your username');
    //     $username.focus();
    //     return;
    // }
    // TODO: Fix:

    if ($sendMessage.val().length === 0) {
        return;
    }

    if (getRole() === 'customer') {
        chatConnection.sendMessage($sendMessage.val());
    } else {
        const user = getUser(getActiveUsername());
        addMessageToHistory('sent', user, user.username, $sendMessage.val());
        chatConnection.sendMessage($sendMessage.val(), user.username);
    }

    const $card = createConversationSentCard($sendMessage.val());
    addMessageCardToScreen($card);

    $sendMessage.val('');
};

const handleChangeUsername = function() {
    // If the username changes, save it to local storage
    if ($username.val().length > 0 && $connectBut.attr('disabled')) {
        chatConnection.sendCredentials($username.val(), getRole());
        $username.attr('disabled', true);
    }

    changeWindowTitle();
};

$(document).ready(function() {

    let storageWsUrl = localStorage.getItem('chat-' + getRole() + '-wsUrl');
    if (storageWsUrl === null) {
        // If there is no storage, then set default
        storageWsUrl = 'ws://localhost:8080/' + getRole();
    }
    $wsUrl = $('#wsUrl');
    $wsUrl.val(storageWsUrl);
    $wsUrl.keypress(function( event ) {
        if ( event.which === 13 ) {
            handleClickConnectBut();
        }
    });

    // Prevent the form submitting when user presses enter.
    $('#connection-details').on('submit',function(e) {
        e.preventDefault();
    });

    $connectBut = $('#connectBut');
    $connectBut.click(function() {
        handleClickConnectBut();
    });

    $disconnectBut = $('#disconnectBut');
    $disconnectBut.click(function() {
        handleClickDisconnectBut();
    });

    $connectionStatus = $('#connection-status');
    $connectionStatus.click(function(event) {
        toggleConnectionDetails();
        event.preventDefault();

    });

    $username = $('#username');
//    const storageUsername = localStorage.getItem('chat-' + getRole() + '-username');
//    if (storageUsername !== null) {
//        // If username was previously store, use it.
//        //$username.val(storageUsername); // TODO: Uncomment and put this code back in
//    }
    $username.blur(function() {
        handleChangeUsername();
    });
    $username.keypress(function( event ) {
        if ( event.which === 13 ) {
            $(this).blur();
        }
    });

    $users = $('#user-list .list-group');

    $conversation = $('#conversation');

    $sendMessage = $('#sendMessage');
    $sendMessage.attr('disabled', true);
    $sendMessage.keypress(function( event ) {
        if ( event.which === 13 ) {
            handleClickSendMessageBut();
        }
    });

    $sendMessageBut = $('#sendMessageBut');
    $sendMessageBut.attr('disabled', true);
    $sendMessageBut.click(function() {
        handleClickSendMessageBut();
    });

    $(window).on('resize', function() {
        handleWindowResize();
    });

    handleWindowResize();

    changeWindowTitle();

    // Connect
    handleClickConnectBut();

});