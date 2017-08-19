/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016 Jelurida IP B.V.
 *
 * See the LICENSE.txt file at the top-level directory of this distribution
 * for licensing information.
 *
 * Unless otherwise agreed in a custom licensing agreement with Jelurida B.V.,
 * no part of the Nxt software, including this file, may be copied, modified,
 * propagated, or distributed except according to the terms contained in the
 * LICENSE.txt file.
 *
 * Removal or modification of this copyright notice is prohibited.
 *
 */

package wcg;

import wcg.util.Logger;

public interface Fee {

    long getFee(TransactionImpl transaction, Appendix appendage);

    Fee DEFAULT_FEE = new Fee.ConstantFee(Constants.ONE_WCG);

    Fee NONE = new Fee.ConstantFee(0L);

    final class ConstantFee implements Fee {

        private final long fee;

        public ConstantFee(long fee) {
            this.fee = fee;
        }

        @Override
        public long getFee(TransactionImpl transaction, Appendix appendage) {
            return fee/Constants.REDUCTOR_FEE;
        }

    }

    abstract class SizeBasedFee implements Fee {

        private final long constantFee;
        private final long feePerSize;
        private final int unitSize;

        public SizeBasedFee(long feePerSize) {
            this(0, feePerSize);
        }

        public SizeBasedFee(long constantFee, long feePerSize) {
            this(constantFee, feePerSize, 1024);
        }

        public SizeBasedFee(long constantFee, long feePerSize, int unitSize) {
            this.constantFee = constantFee;
            this.feePerSize = feePerSize;
            this.unitSize = unitSize;
        }

        // the first size unit is free if constantFee is 0
        @Override
        public final long getFee(TransactionImpl transaction, Appendix appendage) {
            int height = transaction.getHeight();
            int size = getSize(transaction, appendage) - 1;

            if (size < 0) {
                return constantFee/Constants.REDUCTOR_FEE;
            }

            long constantFee = 0;
            long feePerSize = 0;
            long fee = 0;

            if (height <= Constants.NEW_FEE_CALCULATION_BLOCK) {
                constantFee = (this.constantFee < Constants.ONE_WCG ? Constants.ONE_WCG : this.constantFee)/Constants.REDUCTOR_FEE;
                feePerSize = this.feePerSize;

                fee = (Math.addExact(constantFee, Math.multiplyExact((long) (size / unitSize), feePerSize))/Constants.REDUCTOR_FEE);

                return fee < Constants.ONE_WCG/Constants.REDUCTOR_FEE ? Constants.ONE_WCG/Constants.REDUCTOR_FEE : fee;
            }

            if (this.constantFee == 0) constantFee = 0;
            else constantFee = (this.constantFee < Constants.ONE_WCG ? Constants.ONE_WCG : this.constantFee)/Constants.REDUCTOR_FEE;
            if (this.feePerSize == 0) feePerSize = 0;
            else feePerSize = (this.feePerSize < Constants.ONE_WCG ? Constants.ONE_WCG : this.feePerSize)/Constants.REDUCTOR_FEE;

            fee = (Math.addExact(constantFee, Math.multiplyExact((long) (size / unitSize), feePerSize)));

            if (fee != 0) {
                fee = (fee < Constants.ONE_WCG ? Constants.ONE_WCG : fee)/ Constants.REDUCTOR_FEE;
            }
            return fee;
        }

        public abstract int getSize(TransactionImpl transaction, Appendix appendage);

    }

}