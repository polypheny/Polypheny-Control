/*
 * Copyright 2017-2023 The Polypheny Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

var webSocket = new WebSocket("ws://" + location.hostname + ":" + location.port + "/socket/");
var clientId = 0;
var state = "";

var debug = false;

var ansi_up = new AnsiUp;

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
        setClientType();
    }
    if ( data.hasOwnProperty("status") ) { // Periodically sent by server to keep the connection open
        if ( data["status"] === "polyfier" ) {
            $( '#logContent' ).hide();
            $( '#polyfierContent' ).hide();
            $( '#settingsContent' ).hide();
            $( '#dashboardContent' ).hide();
            if ( $('#polyfierStopContent').is(":hidden") ) {
                $( '#polyfierRunningContent' ).show();
            }
        } else if ( data["status"] === "running" ) {
            $( '#btn-start' ).hide();
            $( '#btn-stop' ).show();
            $( '#updateOutputBackButton' ).removeClass('btn-back-disabled');
            if (! $('#polyfierRunningContent').is(":hidden") ) {
                $( '#polyfierRunningContent' ).hide();
                $( '#dashboardContent' ).show();
            }
        } else if ( data["status"] === "idling" ) {
            $( '#btn-stop' ).hide();
            $( '#btn-start' ).show();
            $( '#updateOutputBackButton' ).removeClass('btn-back-disabled');
            if (! $('#polyfierRunningContent').is(":hidden") ) {
                $( '#polyfierRunningContent' ).hide();
                $( '#dashboardContent' ).show();
            }
        } else { // updating
            $( '#logContent' ).hide();
            $( '#configContent' ).hide();
            $( '#settingsContent' ).hide();
            $( '#dashboardContent' ).hide();
            $( '#updateContent' ).show();
            $( '#updateOutputBackButton' ).addClass('btn-back-disabled');
            if (! $('#polyfierRunningContent').is(":hidden") ) {
                $( '#polyfierRunningContent' ).hide();
                $( '#dashboardContent' ).show();
            }
        }
        $( '#footer-right' ).text( "Status: " + data["status"] );
        state = data["status"];
    }
    if ( data.hasOwnProperty( "benchmarkerConnected" ) ) { // Periodically sent by server to keep the connection open
        if ( data["benchmarkerConnected"] === "true" ) {
            $( "body" ).css( "background-color", "#e5983d" );
        } else {
            $( "body" ).css( "background-color", "#3B83C8" );
        }
    }
    if ( data.hasOwnProperty( "version" ) ) { // Periodically sent by server to keep the connection open
        var pdbString = "PDB: " + data["version"]["pdb-branch"] + " @ " + data["version"]["pdb-commit"].substring( 0, 7 );
        var puiString = "PUI: " + data["version"]["pui-branch"] + " @ " + data["version"]["pui-commit"].substring( 0, 7 );
        if ( data["version"]["pdb-behind"] > 0 ) {
            pdbString = "<span style='color: #a90005; font-weight: 900'>" + pdbString + "</span>";
        }
        if ( data["version"]["pui-behind"] > 0 ) {
            puiString = "<span style='color: #a90005; font-weight: 900'>" + puiString + "</span>";
        }
        $( '#footer-middle' ).html( pdbString + " <br> " + puiString );
    }
    if ( data.hasOwnProperty( "startOutput" ) ) {
        appendOutput( $( '#startOutput' ), data["startOutput"] );
    }
    if ( data.hasOwnProperty( "stopOutput" ) ) {
        appendOutput( $( '#stopOutput' ), data["stopOutput"] );
    }
    if ( data.hasOwnProperty( "restartOutput" ) ) {
        appendOutput( $( '#restartOutput' ), data["restartOutput"] );
    }
    if (data.hasOwnProperty("updateOutput")) {
        appendOutput($('#updateOutput'), data["updateOutput"]);
    }
    if (data.hasOwnProperty("logOutput")) {
        if (state === "polyfier") {
            appendOutput($('#logOutputPolyfier'), data["logOutput"]);
        } else {
            appendOutput($('#logOutput'), data["logOutput"]);
        }
    }
    if (data.hasOwnProperty("polyfierOutput")) {
            appendOutput($('#polyfierOutput'), data["polyfierOutput"]);
        }
};

webSocket.onclose = function () {
    $("body").css("background-color", "gray");
};

function appendOutput(box, text) {
    var lines = box.html().split("\n");
    var str = lines.slice(-1000).join("\n");
    box.html(str + "\n" + ansi_up.ansi_to_html(text));
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
    $( '#updateOutputBackButton' ).addClass('btn-back-disabled');
    $( '#dashboardContent' ).hide();
    $( '#config-loading' ).hide();
    $( '#updateContent' ).show();
    sendRequest("control/update");
});

$('#btn-log').click(function () {
    $('#dashboardContent').hide();
    $('#logContent').show();
});

$('#btn-polyfier').click(function () {
    $( '#dashboardContent' ).hide();
    $( '#polyfierContent' ).show();
});

$('#btn-settings').click(function () {
    $( '#dashboardContent' ).hide();
    $( '.tooltip' ).tooltipster( 'hide' );
    $( '#config-loading' ).show();
    updatePdbBranchList();
    // updatePdbBranchList calls updatePuiBranchList in its success method.
    // updatePuiBranchList calls updateConfigPage.
    // updateConfigPage hides the configLoading page and shows the settings page
});


$('.btn-back').click(function () {
    $('#dashboardContent').show();
    $('#updateContent').hide();
    $('#logContent').hide();
    $('#polyfierContent').hide();
    $('#settingsContent' ).hide();
    $('#config-loading' ).hide();
});

$('#saveSettings').click(function () {
    saveConfigs();
});

$('#polyfierStart').click(function () {
    sendRequest("polyfier/start");
});

$('#polyfierStop').click(function () {
    $('#polyfierRunningContent').hide();
    $('#polyfierStopContent').show();
});

$('#polyfierStop-back').click(function () {
    $('#polyfierStopContent').hide();
    if ( state === 'polyfier' ) {
        $('#polyfierRunningContent').show();
    } else {
        $('#dashboardContent').show();
    }
});

$('#polyfierStopForcefully').click(function () {
    sendRequest("polyfier/stopForcefully");
    $('#polyfierStopContent').hide();
    $('#polyfierRunningContent').show();
});

$('#polyfierStopGracefully').click(function () {
    sendRequest("polyfier/stopGracefully");
    $('#polyfierStopContent').hide();
    $('#polyfierRunningContent').show();
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
                            el.prop( "checked", true );
                        }
                    }
                } else {
                    var element = $( "[name='" + key + "']" );
                    if ( element.length ) {
                        element.val( configuration[key] );
                    }
                }
            }
            $( '#config-loading' ).hide();
            $( '#settingsContent' ).show();
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
            console.log( "Error" );
            console.log( data );
        },
        success: function ( data ) {

        },
        url: "config/set",
        data: data
    } );
}

function getControlVersion() {
    var data = {
        "clientId": clientId
    };
    $.ajax( {
        type: "GET",
        error: function ( data ) {
            console.log( "Error" );
            console.log( data );
        },
        success: function ( data ) {
            $( '#footer-left' ).text( "v" + data );
        },
        url: "control/controlVersion",
        data: data
    } );
}

function setClientType() {
    var data = {
        "clientId": clientId,
        "clientType": "BROWSER"
    };
    $.ajax( {
        type: "POST",
        error: function ( data ) {
            console.log( "Error" );
            console.log( data );
        },
        success: function ( data ) {

        },
        url: "client/type",
        data: data
    } );
}

function updatePdbBranchList() {
    var data = {
        "clientId": clientId
    };
    $.ajax( {
        type: "GET",
        error: function ( data ) {
            console.log( "Error" );
            console.log( data );
        },
        success: function ( data ) {
            $( '#pdbBranches' ).empty();
            var branches = JSON.parse( data );
            branches.forEach( function ( value ) {
                $( "#pdbBranches" ).append( new Option( value, value ) );
            } );
            updatePuiBranchList();
        },
        url: "control/pdbBranches",
        data: data
    } );
}

function updatePuiBranchList() {
    var data = {
        "clientId": clientId
    };
    $.ajax( {
        type: "GET",
        error: function ( data ) {
            console.log( "Error" );
            console.log( data );
        },
        success: function ( data ) {
            $( '#puiBranches' ).empty();
            var branches = JSON.parse( data );
            branches.forEach( function ( value ) {
                $( "#puiBranches" ).append( new Option( value, value ) );
            } );
            updateConfigsPage();
        },
        url: "control/puiBranches",
        data: data
    } );
}

$( document ).on( "mouseenter", ".circle", function () {
    $( this ).css( "background-color", "lightgray" );
} );
$( document ).on( "mouseleave", ".circle", function () {
    $( this ).css( "background-color", "white" );
} );

// Activate tooltipster
$( document ).ready( function () {
    $( '.tooltip' ).tooltipster( {
        animation: 'grow',
    } );
} );

// Update version
getControlVersion();

// Change bg color
$( document ).on( 'keyup', function ( e ) {
    if ( debug ) {
        console.log( $( "body" ).css( "background-color" ) );
    }
    if ( e.which === 112 && $( "body" ).css( "background-color" ) !== "rgb(128, 128, 128)" ) {
        if ( $( "body" ).css( "background-color" ) === "rgb(165, 215, 210)" ) {
            $( 'body' ).css( 'background-color', "#3B83C8" );
        } else {
            $('body').css('background-color', "#A5D7D2");
        }
    }
});

