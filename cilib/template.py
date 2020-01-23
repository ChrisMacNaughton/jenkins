from jinja2 import Template


def render(tmpl_file, context):
    """ Renders a jinja template with context
    """
    template = Template(tmpl_file.read_text(), keep_trailing_newline=True)
    return template.render(context)
