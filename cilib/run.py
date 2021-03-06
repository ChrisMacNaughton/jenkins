import subprocess
import click
import shlex
import os
from types import SimpleNamespace


def _log_sub_out(pipe):
    """ Logs output from subprocess
    """
    for line in iter(pipe.readline, b""):
        click.echo(line.decode().strip())


def capture(script, **kwargs):
    """ capture command output
    """
    env = os.environ.copy()
    if not isinstance(script, list) and "shell" not in kwargs:
        script = shlex.split(script)
    process = subprocess.run(
        script, stdout=subprocess.PIPE, stderr=subprocess.PIPE, env=env, **kwargs
    )
    return SimpleNamespace(
        ok=bool(process.returncode == 0),
        returncode=process.returncode,
        stdout=process.stdout,
        stderr=process.stderr,
    )


def cmd_ok(script, **kwargs):
    """ Stream command, doesnt buffer and prints it all out to stdout, only
    returns exit status
    """
    env = os.environ.copy()
    check = None
    if "check" in kwargs:
        check = kwargs["check"]
        del kwargs["check"]
    if not isinstance(script, list) and "shell" not in kwargs:
        script = shlex.split(script)
    process = subprocess.Popen(
        script, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, env=env, **kwargs
    )

    with process.stdout:
        _log_sub_out(process.stdout)
    exitcode = process.wait()
    if check and exitcode > 0:
        raise subprocess.CalledProcessError(exitcode, "", "")
    return SimpleNamespace(ok=bool(exitcode == 0), returncode=exitcode)
