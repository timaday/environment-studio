# Independently invented native-client simulator; no database access.
# Args carry protocol scalars only. Password is read once from owned stdin, never printed.
import sys
engine, mode, nonce, archive, program = sys.argv[1:]
def emit(s):
    sys.stdout.write(s)
    sys.stdout.flush()
def line():
    value = sys.stdin.readline()
    if not value:
        raise SystemExit(0)
    return value
if engine == 'oracle':
    emit('\nSQL*Plus: Release 23.26.3.0.0 - Production on Tue Sep 8 12:34:56 2026\nVersion 23.26.3.0.0\n\nCopyright (c) 1982, 2026, Oracle.  All rights reserved.\n\nSQL> ')
    assert line().startswith('CONNECT MOCKUSER@"')
    emit('Enter password: ')
else:
    emit('Password: ')
assert line() == 'invented-secret\n'
if mode == 'extra-prompt':
    emit('Password: ')
    line()
    raise SystemExit(8)
if engine == 'oracle':
    emit('\nConnected.\nSQL> ')
else:
    assert line() == '\\echo ES_SETTINGS '+nonce+' :ON_ERROR_STOP :ON_ERROR_ROLLBACK\n'
    emit('\nES_SETTINGS '+nonce+' on off\n')
settings={'echo':'echo OFF','feedback':'feedback OFF SQL_ID OFF','heading':'heading OFF','verify':'verify OFF','define':'define OFF','autocommit':'autocommit OFF','exitcommit':'exitcommit OFF','sqlprompt':'sqlprompt ""','sqlnumber':'sqlnumber OFF','pagesize':'pagesize 0','linesize':'linesize 32767','serveroutput':'serveroutput OFF','trimout':'trimout ON','tab':'tab OFF','wrap':'wrap : lines will be truncated'}
started=False
ready=False
committed=False
rolled_back=False
for raw in sys.stdin:
    command=raw.strip()
    if command.startswith('SHOW '):
        emit(settings[command[5:]]+'\n')
    if command in ['SET TRANSACTION READ WRITE;','BEGIN ISOLATION LEVEL READ COMMITTED READ WRITE;']:
        assert not started
        started=True
    if command.startswith('SELECT ') and 'ES_BOOTSTRAP|' in command:
        assert started
        emit('ES_BOOTSTRAP|'+nonce+'\n')
    if command.startswith('SELECT ') and 'ES_READY|' in command:
        assert started and not ready
        ready=True
        if mode == 'earlier-error':
            sys.stderr.write('INDEPENDENT ERROR\n')
            sys.stderr.flush()
        if mode == 'dead-ready':
            emit('ES_READY|'+nonce+'|'+archive+'|'+program+'\n')
            raise SystemExit(0)
        if mode == 'wrong-ready':
            emit('ES_READY|'+'f'*32+'|'+archive+'|'+program+'\n')
        else:
            emit('ES_READY|'+nonce+'|'+archive+'|'+program+'\n')
    if command in ['COMMIT;','COMMIT WRITE IMMEDIATE WAIT;']:
        assert ready and not committed
        assert mode not in ['wrong-ready','earlier-error']
        committed=True
        if mode == 'lost-ack':
            raise SystemExit(0)
    if command.startswith('SELECT ') and 'ES_COMMITTED|' in command:
        assert committed
        emit('ES_COMMITTED|'+nonce+'|'+archive+'|'+program+'\n')
    if command.startswith('SELECT ') and 'ES_ROLLED_BACK|' in command:
        assert not committed
        rolled_back=True
        emit('ES_ROLLED_BACK|'+nonce+'\n')
    if command in ['\\quit','EXIT SUCCESS ROLLBACK']:
        assert committed or rolled_back
        raise SystemExit(0)
