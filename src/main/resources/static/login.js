function login() {
    let name = $('#name').val();
    let password = $('#password').val();
    let authHeader = "Basic " + btoa(name + ":" + password);

    $.ajax({
        url        : document.location.protocol + '//' + document.location.host + '/',
        type       : 'GET',
        headers    : {
            "Authorization": authHeader
        },
        success    : function(data) {
            document.location = document.location.protocol + '//' + document.location.host + '/'
        },
        error      : function(d, data) {
            $('#invalid').css('display', 'block');
        },
    });
    return false;
}

// Function to check if already authenticated and if so, redirects to /
function checkAuthentication() {
    var xhr = new XMLHttpRequest();
    xhr.open('GET', '/session/is_authenticated', true);

    // Set up the onload event
    xhr.onload = function() {
        if (this.status === 200 && this.responseText === "true") {
            // Redirect the user to the root ("/") if response is "true"
            window.location.href = '/';
        }
    };

    // Handle any errors
    xhr.onerror = function() {
        console.error('Request failed.');
    };

    // Send the request
    xhr.send();
}

checkAuthentication();
