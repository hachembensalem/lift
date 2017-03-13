"""
A Convolutional Network implementation example using TensorFlow library.
This example is using the MNIST database of handwritten digits
(http://yann.lecun.com/exdb/mnist/)

Based on Aymeric Damien's project (https://github.com/aymericdamien/TensorFlow-Examples/)
"""

import tensorflow as tf
import json
import os
import numpy as np
import pickle

# Import MNIST data
from tensorflow.examples.tutorials.mnist import input_data


class CNN:
    """
    Convolutional Neural Network. Training and forward-propagation
    """
    verbose = True

    # Create some wrappers for simplicity
    @staticmethod
    def conv2d(x, w, b, strides=1):
        """
        Conv2D wrapper, with bias and relu activation
        :param x:
        :param w:
        :param b:
        :param strides:
        :return:
        """
        x = tf.nn.conv2d(x, w, strides=[1, strides, strides, 1], padding='VALID')  # padding='SAME')
        x = tf.nn.bias_add(x, b)
        return tf.nn.relu(x)

    @staticmethod
    def maxpool2d(x, k=2):
        """
        MaxPool2D wrapper
        :param x:
        :param k:
        :return:
        """
        return tf.nn.max_pool(x, ksize=[1, k, k, 1], strides=[1, k, k, 1],
                              padding='VALID')  # padding='SAME')

    @staticmethod
    def conv_net(x, weights, biases):
        """
        # Create model
        :param x:
        :param weights:
        :param biases:
        :return:
        """
        # Reshape input picture
        x = tf.reshape(x, shape=[-1, 28, 28, 1])

        # Convolution Layer
        conv1 = CNN.conv2d(x, weights['wconv1'], biases['bconv1'])
        # Max Pooling (down-sampling)
        # conv1 = CNNTrainer.maxpool2d(conv1, k=2)

        # Convolution Layer
        conv2 = CNN.conv2d(conv1, weights['wconv2'], biases['bconv2'])
        # Max Pooling (down-sampling)
        # conv2 = CNNTrainer.maxpool2d(conv2, k=2)

        # Fully connected layer
        # Reshape conv2 output to fit fully connected layer input
        fc1 = tf.reshape(conv2, [-1, weights['wmlp1'].get_shape().as_list()[0]])
        fc1 = tf.add(tf.matmul(fc1, weights['wmlp1']), biases['bmlp1'])
        fc1 = tf.nn.relu(fc1)
        # Apply Dropout
        # fc1 = tf.nn.dropout(fc1, dropout)

        # Output, class prediction
        out = tf.add(tf.matmul(fc1, weights['wout']), biases['bout'])
        return out

    def __init__(self, n_kernels, kernel_shape):
        self.mnist = input_data.read_data_sets("/tmp/data/", one_hot=True)

        # Parameters
        self.learning_rate = 0.001
        self.training_iters = 200000
        self.batch_size = 128
        self.display_step = 10

        # Network Parameters
        self.n_kernels = n_kernels  # [16, 32]
        self.kernel_shape = kernel_shape  # (10, 10)
        self.input_len = 784  # MNIST data input (img shape: 28*28)
        self.n_classes = 10  # MNIST total classes (0-9 digits)
        self.dropout = 0.75  # Dropout, probability to keep units

        # tf Graph input
        self.x = tf.placeholder(tf.float32, [None, self.input_len])
        self.y = tf.placeholder(tf.float32, [None, self.n_classes])
        self.keep_prob = tf.placeholder(tf.float32)  # dropout (keep probability)

        # Store layers weight & bias
        self.weights = {
            # 5x5 conv, 1 input, 32 outputs
            # Original dimensions: 5, 5, 1, 32
            'wconv1': tf.Variable(tf.random_normal([self.kernel_shape[0], self.kernel_shape[1], 1, self.n_kernels[0]])),
            # 5x5 conv, 32 inputs, 64 outputs
            # Original dimensions: 5, 5, 32, 64
            'wconv2': tf.Variable(
                tf.random_normal([self.kernel_shape[0], self.kernel_shape[1], self.n_kernels[0], self.n_kernels[1]])),
            # fully connected, 7*7*64 inputs, 1024 outputs
            'wmlp1': tf.Variable(
                tf.random_normal([self.kernel_shape[0] * self.kernel_shape[1] * self.n_kernels[1], 256])),
            # 7*7*64, 1024])),
            # 1024 inputs, 10 outputs (class prediction)
            'wout': tf.Variable(tf.random_normal([256, self.n_classes]))
        }
        self.trained_weights = None

        self.biases = {
            'bconv1': tf.Variable(tf.random_normal([self.n_kernels[0]])),
            'bconv2': tf.Variable(tf.random_normal([self.n_kernels[1]])),
            'bmlp1': tf.Variable(tf.random_normal([256])),
            'bout': tf.Variable(tf.random_normal([self.n_classes]))
        }
        self.trained_biases = None

        # Construct model
        self.pred = CNN.conv_net(self.x, self.weights, self.biases)  # , keep_prob)

        # Define loss and optimizer
        self.cost = tf.reduce_mean(tf.nn.softmax_cross_entropy_with_logits(logits=self.pred, labels=self.y))
        self.optimizer = tf.train.AdamOptimizer(learning_rate=self.learning_rate).minimize(self.cost)

        # Evaluate model
        self.correct_pred = tf.equal(tf.argmax(self.pred, 1), tf.argmax(self.y, 1))
        self.accuracy = tf.reduce_mean(tf.cast(self.correct_pred, tf.float32))

        # Initializing the variables
        self.init = tf.global_variables_initializer()

        # Session configuration
        self.config = tf.ConfigProto(device_count={'GPU': 0})

        # Get experiment directory name
        self.dir_name = CNN.get_dir_name(self.n_kernels, self.kernel_shape)
        # Create directory
        if not os.path.isdir(self.dir_name):
            os.mkdir(self.dir_name)


    @staticmethod
    def get_dir_name(n_kernels, kernel_shape):
        """
        Forms a directory name for a given experiment.
        :param n_kernels:
        :param kernel_shape:
        :return:
        """
        return "experiment." + str(n_kernels[0]) + "." + str(kernel_shape[0]) + "." + str(kernel_shape[1])

    @staticmethod
    def restore(n_kernels, kernel_shape):
        """
        Restore data if requested and possible.
        :return:
        """
        dir_name = CNN.get_dir_name(n_kernels, kernel_shape)
        if os.path.isfile(os.path.join(dir_name, "pickled_acnn.p")):
            return pickle.load(open(os.path.join(dir_name, "pickled_acnn.p"), "rb"))
        else:
            return None

    def train(self):
        """
        Trains the network
        """
        # Launch the graph
        with tf.Session(config=self.config) as sess:
            sess.run(self.init)
            step = 1
            # Keep training until reach max iterations
            acc = 0
            # while step * batch_size < training_iters:
            while acc < 0.8:
                batch_x, batch_y = self.mnist.train.next_batch(self.batch_size)
                # Run optimization op (backprop)
                sess.run(self.optimizer, feed_dict={self.x: batch_x, self.y: batch_y,
                                                    self.keep_prob: self.dropout})
                if step % self.display_step == 0:
                    # Calculate batch loss and accuracy
                    loss, acc = sess.run([self.cost, self.accuracy], feed_dict={self.x: batch_x,
                                                                                self.y: batch_y})
                    print("Iter " + str(step * self.batch_size) + ", Minibatch Loss= " +
                          "{:.6f}".format(loss) + ", Training Accuracy= " +
                          "{:.5f}".format(acc))
                step += 1
            print("Optimization Finished!")

            # Calculate accuracy for 256 mnist test images
            print("Testing Accuracy:",
                  sess.run(self.accuracy, feed_dict={self.x: self.mnist.test.images[:256],
                                                     self.y: self.mnist.test.labels[:256],
                                                     self.keep_prob: 1.}))
            # Backup params
            self.trained_weights = sess.run(self.weights)
            self.trained_biases = sess.run(self.biases)

        trained_params = {}
        for weight_key in self.trained_weights:
            if len(self.trained_weights[weight_key].shape) == 2:
                # MLP weights
                trained_params[weight_key] = self.trained_weights[weight_key].transpose()
            else:
                # Convolutional weights
                trained_params[weight_key] = self.trained_weights[weight_key]
        trained_params = {**trained_params, **self.trained_biases}

        for param_name in trained_params:
            json_string = json.dumps(trained_params[param_name].astype(np.float16).tolist())
            print(trained_params[param_name].shape)
            with open(self.dir_name + '/' + param_name + '.json', 'w') as outfile:
                outfile.write(json_string)
                outfile.close()
            print("Saved param \"" + param_name + "\"")

    def fprop(self, n_batches, n_inputs):
        """
        Forward-propagation; saves input samples and classification results as files.
        :param n_inputs:
        :param n_batches:
        """
        test_images = np.empty([n_batches, n_inputs, 28, 28, 1])
        test_results = np.empty([n_batches, n_inputs, 10])
        test_targets = np.empty([n_batches, n_inputs, 10])

        # Convert arrays to tensors
        trained_weights_tensors = {}
        for weight_name in self.trained_weights:
            trained_weights_tensors[weight_name] = tf.convert_to_tensor(
                self.trained_weights[weight_name].astype(np.float16).astype(np.float32))

        trained_biases_tensors = {}
        for bias_name in self.trained_biases:
            trained_biases_tensors[bias_name] = tf.convert_to_tensor(
                self.trained_biases[bias_name].astype(np.float16).astype(np.float32))

        input_len = 784
        x = tf.placeholder("float", [None, input_len])

        for batch_no in np.arange(0, n_batches):
            test_batch_images_flat, test_targets[batch_no] = self.mnist.test.next_batch(n_inputs)

            test_images[batch_no] = np.reshape(test_batch_images_flat, [-1, 28, 28, 1])

            print("Forward-propagating...")

            # Construct model
            pred = self.conv_net(x, trained_weights_tensors, trained_biases_tensors)

            # Initializing the variables
            init = tf.global_variables_initializer()

            # Launch the graph
            with tf.Session(config=self.config) as sess:
                sess.run(init)
                # Produce outputs
                test_results[batch_no] = sess.run([pred], feed_dict={x: test_batch_images_flat})[0]

        # Save test images
        json_string = json.dumps(test_images.astype(np.float32).tolist())
        with open(self.dir_name + '/test_images_n' + str(n_inputs) + '.json', 'w') as outfile:
            outfile.write(json_string)
            outfile.close()
        print("Saved (" + str(test_images.shape[0] * test_images.shape[1]) + ") images, shape: ", end='')
        print(test_images.shape)

        # Save Tensorflow's forward propagation results into a JSON file
        json_string = json.dumps(test_results[0].astype(np.float32).tolist())
        with open(self.dir_name + '/test_tf_results_n' + str(n_inputs) + '.json', 'w') as outfile:
            outfile.write(json_string)
            outfile.close()
        if self.verbose:
            print("Saved results, shape: ", end='')
            print(test_results[0].shape)

        # Print results
        if self.verbose:
            np.set_printoptions(threshold=np.inf, suppress=True)
            # print("Weights[0][0]:")
            # print(trained_weights)
            input_no = 0
            batch_no = 0
            print("Inputs[" + str(batch_no) + "][" + str(input_no) + "]:")
            print(test_images[batch_no][input_no].sum())
            print("Weights['wmlp1'][0]:")
            print(self.trained_weights['wmlp1'][0])
            print("Biases['bmlp1']:")
            print(self.trained_biases['bmlp1'])
            print("Output[" + str(batch_no) + "][" + str(input_no) + "]:")
            print(test_results[0][batch_no][input_no].shape)
            print(test_results[0][batch_no][input_no])
            print("Output[" + str(batch_no) + "][0:" + str(input_no + 1) + "] maxed:")
            print([list(decision).index(max(decision)) for decision in test_results[batch_no][:input_no + 1]])
            print("Correct[" + str(batch_no) + "][0:" + str(input_no + 1) + "]:")
            print([list(decision).index(max(decision)) for decision in test_targets[batch_no][:input_no + 1]])

