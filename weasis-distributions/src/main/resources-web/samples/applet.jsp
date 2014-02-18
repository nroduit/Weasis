<body></body>
<script src="https://www.java.com/js/deployJava.js"></script>
<script type="text/javascript" src="http://ajax.googleapis.com/ajax/libs/jquery/1.8.3/jquery.min.js"></script>
<script>
    var $container = $(document);
    deployJava.runApplet({
        width: $container.width() - 25,
        height: $container.height() - 20
    }, {
        jnlp_href: 'applet.jnlp',
        commands: '${param.commands}'
    }, '1.6');
</script>
