package omfg;

import edu.rice.hj.api.HjSuspendable;
import edu.rice.hj.api.HjSuspendingCallable;
import edu.rice.hj.api.HjSuspendingProcedure;
import edu.rice.hj.api.SuspendableException;

import static edu.rice.hj.Module1.async;

/**
 * Created by matthewfl
 */
public class HabaneroExtra {

    public static <T> void forasyncItems(final int numTasks, final HjSuspendingCallable<T> next, final HjSuspendingProcedure<T> body) throws SuspendableException {
        final HjSuspendable asyncBody = new HjSuspendable() {
            @Override
            public void run() throws SuspendableException {
                do {
                    final T loopItem = next.call();
                    if (loopItem != null) {
                        body.apply(loopItem);
                    } else {
                        break;
                    }
                } while (true);
            }
        };

        for (int i = 0; i < numTasks; i++) {
            async(asyncBody);
        }
    }

}
