class Client {
  constructor() {
    this.identity = String(Math.floor(Math.random() * 10000)).padStart(4, '0');
    this.state = 'active';
    this.commandQueue = [];
    this.processingIndex = 0;
  }

  receiveCommand(command) {
    if (!command || !command.target_id || !command.type) return;

    if (this.state === 'waiting') {
      if (command.type === 'exit' && command.target_id === this.identity) {
        this.state = 'active';
        this.processQueue();
      } else {
        this.commandQueue.push(command);
      }
      return;
    }

    this.commandQueue.push(command);
    this.processQueue();
  }

  processQueue() {
    if (this.state !== 'active') return;
    while (this.processingIndex < this.commandQueue.length && this.state === 'active') {
      const cmd = this.commandQueue[this.processingIndex];
      this.processingIndex++;

      if (cmd.target_id !== this.identity) continue;
      if (cmd.type === 'exit') {
        this.state = 'waiting';
        console.log(`Client ${this.identity} waiting`);
        break;
      } else {
        this.executeCommand(cmd);
      }
    }
  }

  executeCommand(cmd) {
    console.log(`Client ${this.identity} executing ${cmd.type}`);
  }
}

class MessageBroker {
  constructor() {
    this.clients = [];
  }

  register(client) {
    this.clients.push(client);
  }

  broadcast(command) {
    console.log(`Broadcast ${command.type} to ${command.target_id}`);
    this.clients.forEach(client => client.receiveCommand(command));
  }
}

const broker = new MessageBroker();
const client1 = new Client();
const client2 = new Client();
console.log(`Client 1: ${client1.identity}, Client 2: ${client2.identity}`);
broker.register(client1);
broker.register(client2);

broker.broadcast({ target_id: client1.identity, type: 'process' });
broker.broadcast({ target_id: client2.identity, type: 'update' });
broker.broadcast({ target_id: client1.identity, type: 'exit' });
broker.broadcast({ target_id: client1.identity, type: 'process' });
broker.broadcast({ target_id: client1.identity, type: 'exit' });
