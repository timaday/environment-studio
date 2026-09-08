# Independently invented process-boundary adversary. No database or credentials.
import sys
import time
case = sys.argv[1]
if case == 'earlier-error':
    sys.stderr.write('ERROR\n')
    sys.stderr.flush()
    print('READY')
elif case == 'trickle':
    print('R', end='', flush=True)
    time.sleep(5)
elif case == 'overflow':
    sys.stdout.write('x' * 1100000)
    sys.stdout.flush()
elif case == 'blocked-input':
    print('BLOCKED', flush=True)
    time.sleep(10)
elif case == 'environment':
    import os
    assert set(os.environ) <= {'LANG', 'LC_ALL'}
    assert os.getsid(0) == os.getpid()
    try:
        os.open('/dev/tty', os.O_RDONLY)
        raise SystemExit(8)
    except OSError:
        pass
    print('CLEAN')
else:
    raise SystemExit(7)
