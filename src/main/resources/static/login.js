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
            console.log(data);
            $('#invalid').css('display', 'block');
        },
    });
    return false;
}