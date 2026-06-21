package com.example.myapp

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TransactionAdapter(
    private val onItemClick: (TransactionEntity) -> Unit
) : RecyclerView.Adapter<TransactionAdapter.ViewHolder>() {
    private var transactions: List<TransactionEntity> = emptyList()

    val currentList: List<TransactionEntity>
        get() = transactions

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transaction, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = transactions.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val txn = transactions[position]
        val context = holder.itemView.context

        holder.date.text = txn.date
        holder.given.text = if (txn.given != 0.0) "₹%.2f".format(txn.given) else ""
        holder.received.text = if (txn.received != 0.0) "₹%.2f".format(txn.received) else ""
        holder.date.setTextColor(context.getColor(R.color.text_primary))
        holder.given.setTextColor(context.getColor(R.color.color_given))
        holder.received.setTextColor(context.getColor(R.color.color_received))
        
        // Set background color for striping
        val bgColor = if (position % 2 == 0)
            context.getColor(android.R.color.white)
        else
            context.getColor(R.color.lightStripeGray)
        holder.itemView.setBackgroundColor(bgColor)

        when {
            txn.balance < 0 -> {
                holder.balance.setTextColor(context.getColor(R.color.color_given))
                holder.balance.text = "₹%.2f".format(-txn.balance)
            }
            txn.balance > 0 -> {
                holder.balance.setTextColor(context.getColor(R.color.color_received))
                holder.balance.text = "+₹%.2f".format(txn.balance)
            }
            else -> {
                holder.balance.setTextColor(context.getColor(R.color.text_primary))
                holder.balance.text = "₹0.00"
            }
        }
        holder.itemView.setOnClickListener { onItemClick(txn) }
    }

    fun submitList(list: List<TransactionEntity>) {
        transactions = list
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val date: TextView = view.findViewById(R.id.tvDate)
        val given: TextView = view.findViewById(R.id.tvGiven)
        val received: TextView = view.findViewById(R.id.tvReceived)
        val balance: TextView = view.findViewById(R.id.tvBalance)
    }
}