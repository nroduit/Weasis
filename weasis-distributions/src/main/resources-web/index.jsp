<input id="commands" name="commands" style="width: 100%"> <br>
<script type="text/javascript">
    function go(comp) {
        if (commands.value)
            comp.href += '?commands=' + encodeURIComponent(commands.value);
    }
</script>
<a href="samples/applet.jsp" onclick="go(this)">Applet</a> <br>
<a href="samples/frame.jsp" onclick="go(this)">Frame</a> <br>