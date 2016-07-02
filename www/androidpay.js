var exec = require('cordova/exec');

var AndroidPay = {
	
	canMakePayments: function (successCallback, errorCallback) {
		exec(successCallback, errorCallback, 'AndroidPay', 'canMakePayments', []);
	},
	
	makePaymentRequest: function (order, successCallback, errorCallback) {
		exec(successCallback, errorCallback, 'AndroidPay', 'makePaymentRequest', [order]);
	}
	
};

module.exports = AndroidPay;
