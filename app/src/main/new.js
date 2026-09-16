exports.onJobAssigned = functions.firestore
    .document('chats_v2/{chatId}')
    .onUpdate((change, context) => {
        const newData = change.after.data();
        const oldData = change.before.data();
        if (newData.assignedTechnician !== oldData.assignedTechnician) {
            // Logic: Find Tech's fcmToken in 'users' collection
            // Logic: admin.messaging().sendToDevice(token, { notification: { title: 'New Job!', body: 'You have been assigned to ' + newData.customerName } });
        }
    });