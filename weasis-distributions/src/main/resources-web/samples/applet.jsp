<script src="https://www.java.com/js/deployJava.js"></script>
<script type="text/javascript" src="http://ajax.googleapis.com/ajax/libs/jquery/1.8.3/jquery.min.js"></script>
<script>
    deployJava.runApplet({
        width: $(document).width() - 50,
        height: $(document).height() - 50
    }, {
        jnlp_href: 'applet.jnlp',
        commands: '${param.commands}'
    }, '1.7');
</script>
