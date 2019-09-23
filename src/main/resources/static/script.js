/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2019 Databases and Information Systems Research Group, University of Basel, Switzerland
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

var webSocket = new WebSocket("ws://" + location.hostname + ":" + location.port + "/socket/");
var clientId = 0;

var debug = false;

webSocket.onmessage = function (msg) {
    if (debug) {
        console.log("Received message: " + msg.data);
    }
    var data = JSON.parse(msg.data);
    if (data.hasOwnProperty("clientId")) {
        clientId = data["clientId"];
        if (debug) {
            console.log("Received Client ID: " + clientId);
        }
    }
    if (data.hasOwnProperty("status")) { // Periodically sent by server to keep the connection open
        if (data["status"] === "true") {
            $('#btn-start').hide();
            $('#btn-stop').show();
        } else {
            $('#btn-stop').hide();
            $('#btn-start').show();
        }
    }
    if (data.hasOwnProperty("startOutput")) {
        appendOutput($('#startOutput'), data["startOutput"]);
    }
    if (data.hasOwnProperty("stopOutput")) {
        appendOutput($('#stopOutput'), data["stopOutput"]);
    }
    if (data.hasOwnProperty("restartOutput")) {
        appendOutput($('#restartOutput'), data["restartOutput"]);
    }
    if (data.hasOwnProperty("updateOutput")) {
        appendOutput($('#updateOutput'), data["updateOutput"]);
    }
    if (data.hasOwnProperty("logOutput")) {
        appendOutput($('#logOutput'), data["logOutput"]);
    }
};

webSocket.onclose = function () {
    $("body").css("background-color", "gray");
};

function appendOutput(box, text) {
    var lines = box.text().split("\n");
    var str = lines.slice(-10000).join("\n");
    box.text(str + "\n" + text);
    box.scrollTop(box[0].scrollHeight);
}

$('#btn-start').click(function () {
    $('#dashboardContent').hide();
    $('#logContent').show();
    sendRequest("control/start");
});

$('#btn-stop').click(function () {
    $('#dashboardContent').hide();
    $('#logContent').show();
    sendRequest("control/stop");
});

$('#btn-restart').click(function () {
    $('#dashboardContent').hide();
    $('#logContent').show();
    sendRequest("control/restart");
});

$('#btn-update').click(function () {
    $('#dashboardContent').hide();
    $('#updateContent').show();
    sendRequest("control/update");
});

$('#btn-log').click(function () {
    $('#dashboardContent').hide();
    $('#logContent').show();
});

$('#btn-config').click(function () {
    updateConfigsPage();
    $('#dashboardContent').hide();
    $('#configContent').show();
});

$('#btn-settings').click(function () {
    updateConfigsPage();
    $('#dashboardContent').hide();
    $('#settingsContent').show();
});

$('.btn-back').click(function () {
    $('#dashboardContent').show();
    $('#updateContent').hide();
    $('#logContent').hide();
    $('#configContent').hide();
    $('#settingsContent').hide();
});

$('#saveConfigs').click(function () {
    saveConfigs();
});

$('#saveSettings').click(function () {
    saveConfigs();
});

function sendRequest(url) {
    $('.btn-back').prop('disabled', true);
    var data = {
        "clientId": clientId
    };
    $.ajax({
        type: "POST",
        error: function (data) {
            console.log("Error");
            console.log(data);
            $('.btn-back').prop('disabled', false);
        },
        success: function (data) {
            $('.btn-back').prop('disabled', false);
        },
        url: url,
        data: data
    });
}

function updateConfigsPage() {
    var data = {
        "clientId": clientId
    };
    $.ajax({
        type: "GET",
        error: function (data) {
            console.log("Error");
            console.log(data);
        },
        success: function (data) {
            var configuration = JSON.parse(data);
            for (var key in configuration) {
                // Check if it is an array
                if (configuration[key].charAt(0) === "[" && configuration[key].charAt(configuration[key].length - 1) === "]") {
                    var array = JSON.parse(configuration[key]);
                    for (var k in array) {
                        var el = $("[value='" + array[k] + "']");
                        if (el.length) {
                            el.prop("checked", true);
                        }
                    }
                } else {
                    var element = $("[name='" + key + "']");
                    if (element.length) {
                        element.val(configuration[key]);
                    }
                }
            }
        },
        url: "config/get",
        data: data
    });
}

function saveConfigs() {
    var config = JSON.stringify($('#configs').serializeJSON());
    var data = {
        "clientId": clientId,
        "config": config
    };
    $.ajax({
        type: "POST",
        error: function (data) {
            console.log("Error");
            console.log(data);
        },
        success: function (data) {

        },
        url: "config/set",
        data: data
    });
}

$(document).on("mouseenter", ".circle", function () {
    $(this).css("background-color", "lightgray");
});
$(document).on("mouseleave", ".circle", function () {
    $(this).css("background-color", "white");
});

// Change bg color
$(document).on('keyup', function (e) {
    if (debug) {
        console.log($("body").css("background-color"));
    }
    if (e.which === 112 && $("body").css("background-color") !== "rgb(128, 128, 128)") {
        if ($("body").css("background-color") === "rgb(165, 215, 210)") {
            $('body').css('background-color', "#3B83C8");
        } else {
            $('body').css('background-color', "#A5D7D2");
        }
    }
});
